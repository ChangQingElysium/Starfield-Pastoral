package com.stardew.craft.event;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.core.FarmAreaResolver;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.core.ModGameRules;
import com.stardew.craft.farm.FarmInstance;
import com.stardew.craft.greenhouse.GreenhouseInteriorCache;
import com.stardew.craft.interior.PlayerInteriorAllocator;
import com.stardew.craft.manager.CoalForestArea;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/**
 * 农场区域保护：
 * - 非农场区域（公共区域）默认不可放置/破坏方块，可通过游戏规则开放
 * - 别人的农场：无权限(0)不可进入，仅访问权限(1)不可修改方块，完全权限(2)可操作
 * - 自己的农场：完全权限
 * - 创造模式不受限
 */
@EventBusSubscriber(modid = StardewCraft.MODID)
public class FarmAreaProtectionEvents {

    private static void denyBuilding(ServerPlayer player) {
        com.stardew.craft.network.GlobalHudMessagePayload.sendTo(player,Component.translatable("stardewcraft.farm.protected"));
    }

    /**
     * 破坏方块：BreakEvent 取消安全（方块不会被破坏，无物品丢失）。
     */
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (com.stardew.craft.building.runtime.BuildingProtection.protects(level,event.getPos())) {
            event.setCanceled(true);
            if(event.getPlayer() instanceof ServerPlayer player) denyBuilding(player);
            return;
        }
        if (level.dimension() != ModDimensions.STARDEW_VALLEY) {
            return;
        }
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        if (player.isCreative()) {
            return;
        }
        if (isCoalForestChopExempt(event.getPos(), event.getState())) {
            return;
        }
        if (com.stardew.craft.communitycenter.quarry.QuarryAccessManager.isInQuarryArea(event.getPos())
                && !level.getGameRules().getBoolean(ModGameRules.RULE_STARDEW_ALLOW_PUBLIC_BUILDING)
                && !com.stardew.craft.manager.QuarrySpawnService.canPlayerBreakInQuarry(event.getState())) {
            event.setCanceled(true);
            return;
        }
        // 温室外观区域不可破坏
        if (com.stardew.craft.greenhouse.GreenhouseManager.isInGreenhouseExterior(level, event.getPos())) {
            event.setCanceled(true);
            return;
        }
        // 温室内部按 owner/farm 权限决定是否允许破坏。
        if (com.stardew.craft.greenhouse.GreenhouseManager.isInGreenhouseInterior(level, event.getPos())) {
            if (!canModifyGreenhouseAt(player, level, event.getPos())) {
                event.setCanceled(true);
                com.stardew.craft.network.GlobalHudMessagePayload.sendTo(player,
                        Component.translatable("stardewcraft.farm.build_farm_only"));
            } else if (isOriginalGreenhouseStructureBlock(level, event.getPos())) {
                event.setCanceled(true);
            }
            return;
        }
        if (event.getState().is(ModBlocks.CRAB_POT.get()) && !canAccessCrabPot(level, event.getPos(), player)) {
            event.setCanceled(true);
            com.stardew.craft.network.GlobalHudMessagePayload.sendTo(player,Component.translatable("message.stardew_craft.crab_pot.not_owner"));
            return;
        }
        if (isPublicWaterCrabPot(level, event.getPos())) {
            return;
        }
        if (!canBuildAt(player, event.getPos())) {
            event.setCanceled(true);
            com.stardew.craft.network.GlobalHudMessagePayload.sendTo(player,
                    Component.translatable("stardewcraft.farm.build_farm_only"));
        }
    }

    /**
     * Placement cancellation is transactional in NeoForge: captured block snapshots are
     * restored and the pre-placement item stack is retained. Manual restore/destroy logic
     * bypasses that transaction and can consume or duplicate the held item.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event instanceof BlockEvent.EntityMultiPlaceEvent) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (com.stardew.craft.building.runtime.BuildingProtection.protects(level,event.getPos())) {
            event.setCanceled(true);
            if(event.getEntity() instanceof ServerPlayer player) denyBuilding(player);
            return;
        }
        if (level.dimension() != ModDimensions.STARDEW_VALLEY) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.isCreative()) {
            return;
        }

        BlockPos pos = event.getPos();
        if (mayCompletePlacement(
                level,
                player,
                pos,
                event.getBlockSnapshot().getState())) {
            return;
        }

        event.setCanceled(true);
        if (player != null) {
            com.stardew.craft.network.GlobalHudMessagePayload.sendTo(player,
                    Component.translatable("stardewcraft.farm.build_farm_only"));
        }
    }

    /**
     * 判断玩家是否可以在指定位置修改方块。
     * - 非农场区域 → 由公共区域游戏规则决定
     * - 自己的实例化农场 → 可以
     * - 别人的实例化农场 → 需要 PERM_FULL(2)
     *
     * 可被外部调用（镰刀、作物交互、工具等自定义逻辑需要统一权限检查）。
     */
    public static boolean canModifyAt(ServerPlayer player, BlockPos pos) {
        if(com.stardew.craft.interior.FarmCaveRuntime.fixed(player.serverLevel(),pos))return false;
        if (com.stardew.craft.building.runtime.BuildingProtection.protects(player.serverLevel(),pos)) return false;
        // 采石场区域：所有玩家都可以挖掘/放置（非农场但属于公共可操作区）
        if (com.stardew.craft.communitycenter.quarry.QuarryAccessManager.isInQuarryArea(pos)) {
            return true;
        }
        // 查找该位置属于哪个农场
        java.util.UUID ownerUUID = FarmAreaResolver.getOwnerAt(pos);
        if (ownerUUID == null) {
            return player.serverLevel().getGameRules().getBoolean(ModGameRules.RULE_STARDEW_ALLOW_PUBLIC_BUILDING)
                    && isKnownPublicPlacementTarget(player.serverLevel(), pos)
                    && !com.stardew.craft.greenhouse.GreenhouseManager
                            .isInGreenhouseExterior(player.serverLevel(), pos);
        }

        // 自己的农场（owner 或 member）
        FarmInstance farm = com.stardew.craft.farm.FarmInstanceRegistry.get().getFarm(ownerUUID);
        if (farm != null && farm.isFarmer(player.getUUID())) return true;

        // 别人的农场：检查权限
        return com.stardew.craft.farm.FarmPermissionManager.get()
                .canModify(ownerUUID, player.getUUID());
    }

    /** Construction and interaction share the public-area rule; farm ownership still takes precedence. */
    public static boolean canBuildAt(ServerPlayer player, BlockPos pos) {
        return canModifyAt(player, pos);
    }

    /** Permission gate for entity-backed decorations which do not emit BlockEvent placement/break events. */
    public static boolean canModifyDecorationAt(ServerPlayer player, ServerLevel level, BlockPos pos) {
        var buildings=com.stardew.craft.building.runtime.BuildingWorldData.peek(level.getServer());
        if(buildings!=null){var id=buildings.occupying(level.dimension().location(),pos);var building=id==null?null:buildings.find(id);
            if(building!=null && (buildings.transfer(id)!=null || building.phase()==com.stardew.craft.building.runtime.BuildingRecord.Phase.CONSTRUCTING
                    || building.phase()==com.stardew.craft.building.runtime.BuildingRecord.Phase.UPGRADING && com.stardew.craft.building.runtime.BuildingProtection.protects(level,pos)))return false;
        }

        if (player.isCreative() || level.dimension() != ModDimensions.STARDEW_VALLEY) {
            return true;
        }
        if (com.stardew.craft.greenhouse.GreenhouseManager.isInGreenhouseExterior(level, pos)) {
            return false;
        }
        if (com.stardew.craft.greenhouse.GreenhouseManager.isInGreenhouseInterior(level, pos)) {
            return canModifyGreenhouseAt(player, level, pos);
        }
        return canBuildAt(player, pos);
    }

    public static boolean isProtectedNonFarmArea(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null || level.dimension() != ModDimensions.STARDEW_VALLEY) {
            return false;
        }
        if (com.stardew.craft.greenhouse.GreenhouseManager.isInGreenhouseInterior(level, pos)) {
            return false;
        }
        if (com.stardew.craft.communitycenter.quarry.QuarryAccessManager.isInQuarryArea(pos)) {
            return false;
        }
        return FarmAreaResolver.isInStardewButNotFarm(level, pos);
    }

    /**
     * 判断玩家是否在别人的受保护农场上（没有 PERM_FULL 权限）。
     * 与 canModifyAt 的区别：非农场区域（城镇等）返回 false（允许交互），
     * 而 canModifyAt 对非农场区域按公共区域游戏规则判断。
     */
    public static boolean isOnProtectedFarm(ServerPlayer player, BlockPos pos) {
        java.util.UUID ownerUUID = FarmAreaResolver.getOwnerAt(pos);
        if (ownerUUID == null) return false; // 不在任何农场内 → 非受保护区域
        // 自己的农场（owner 或 member）
        FarmInstance farm = com.stardew.craft.farm.FarmInstanceRegistry.get().getFarm(ownerUUID);
        if (farm != null && farm.isFarmer(player.getUUID())) return false;

        return !com.stardew.craft.farm.FarmPermissionManager.get()
                .canModify(ownerUUID, player.getUUID());
    }

    private static boolean isCoalForestChopExempt(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        if (!CoalForestArea.containsColumn(pos)) {
            return false;
        }
        if (state.getBlock() == ModBlocks.LARGE_STUMP.get() || state.getBlock() == ModBlocks.HOLLOW_LOG.get()) {
            return true;
        }
        return com.stardew.craft.tree.WildTrees.isAnyWildTreePart(state);
    }

    /**
     * STARDEW_VALLEY 维度内：
     * 1. 禁止草方块被锄成耕地（所有锄头）
     * 2. 禁止 MC 原版锄头在农场区域使用（太超模，又快又好）— 只允许模组 HoeItem
     */
    @SubscribeEvent
    public static void onBlockToolModification(BlockEvent.BlockToolModificationEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (level.dimension() != ModDimensions.STARDEW_VALLEY) return;
        if (event.getItemAbility() != ItemAbilities.HOE_TILL) return;
        if (event.getPlayer() instanceof ServerPlayer sp && sp.isCreative()) return;

        // 草方块始终禁止被锄（无论什么工具）
        if (event.getState().getBlock() instanceof net.minecraft.world.level.block.GrassBlock) {
            event.setCanceled(true);
            return;
        }

        // 公共主区域的普通黄土不允许直接锄成耕地；只有远古斑点黄土允许挖。
        if ((event.getState().is(ModBlocks.YELLOW_DIRT.get()) || event.getState().is(ModBlocks.DIRT.get()))
                && FarmAreaResolver.isInStardewButNotFarm(level, event.getPos())
                && !com.stardew.craft.greenhouse.GreenhouseManager.isInGreenhouseInterior(level, event.getPos())) {
            event.setCanceled(true);
            if (event.getPlayer() instanceof ServerPlayer player) {
                com.stardew.craft.network.GlobalHudMessagePayload.sendTo(player,
                        Component.translatable("stardewcraft.farm.build_farm_only"));
            }
            return;
        }

        if (event.getPlayer() instanceof ServerPlayer player
            && com.stardew.craft.greenhouse.GreenhouseManager.isInGreenhouseInterior(level, event.getPos())
            && !canModifyGreenhouseAt(player, level, event.getPos())) {
            event.setCanceled(true);
            com.stardew.craft.network.GlobalHudMessagePayload.sendTo(player,
                Component.translatable("stardewcraft.farm.build_farm_only"));
            return;
        }

        // 在农场区域内，只允许模组 HoeItem，禁止 MC 原版锄头
        if (event.getPlayer() != null) {
            net.minecraft.world.item.ItemStack tool = event.getPlayer().getMainHandItem();
            if (!(tool.getItem() instanceof com.stardew.craft.item.tool.HoeItem)) {
                if (com.stardew.craft.core.FarmAreaResolver.isInAnyFarm(level, event.getPos())) {
                    event.setCanceled(true);
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockMultiPlace(BlockEvent.EntityMultiPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        for(var snapshot:event.getReplacedBlockSnapshots()) if(com.stardew.craft.building.runtime.BuildingProtection.protects(level,snapshot.getPos())) {
            event.setCanceled(true);if(event.getEntity() instanceof ServerPlayer player)denyBuilding(player);return;
        }
        if (level.dimension() != ModDimensions.STARDEW_VALLEY) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player && player.isCreative()) {
            return;
        }

        for (var snapshot : event.getReplacedBlockSnapshots()) {
            ServerPlayer player = event.getEntity() instanceof ServerPlayer serverPlayer
                    ? serverPlayer
                    : null;
            if (mayCompletePlacement(
                    level,
                    player,
                    snapshot.getPos(),
                    snapshot.getState())) {
                continue;
            }

            event.setCanceled(true);
            if (player != null) {
                com.stardew.craft.network.GlobalHudMessagePayload.sendTo(player,
                        Component.translatable(
                                "stardewcraft.farm.build_farm_only"));
            }
            return;
        }
    }

    @SubscribeEvent
    public static void onFluidPlace(BlockEvent.FluidPlaceBlockEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (isProtectedNonFarmArea(level, event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        event.getAffectedBlocks().removeIf(pos -> isProtectedNonFarmArea(level, pos));
    }

    // ═══════════════════════════════════════════════════════════
    // 右键交互保护：阻止在别人农场上使用机器、箱子、收割作物等
    // ═══════════════════════════════════════════════════════════

    /**
     * 右键方块：阻止在别人农场上进行任何方块交互。
     * 覆盖所有 useWithoutItem / useItemOn / item.useOn 的调用。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (com.stardew.craft.building.runtime.BuildingProtection.blockInteraction(event)) {
            if (event.getEntity() instanceof ServerPlayer player) denyBuilding(player);
            return;
        }
        if (event.getEntity().level().dimension() != ModDimensions.STARDEW_VALLEY) {
            return;
        }

        // A pinned document is a virtual interaction, not access to the block below it.
        // Document services authorize the eventual build/upgrade on the server.
        if (event.getItemStack().getItem() instanceof com.stardew.craft.building.runtime.BuildingBlueprintItem
                || event.getItemStack().getItem() instanceof com.stardew.craft.building.runtime.BuildingUpgradePermitItem) {
            event.setUseBlock(TriState.FALSE);
            return;
        }

        // Fixed flower pots in the public map must be blocked on both sides.
        // FlowerPotBlock mutates client state immediately, so a server-only
        // cancellation still lets the local player appear to take the flower.
        // The farm-instance region is coordinate-defined and therefore safe to
        // check on the client without relying on the server-only farm registry.
        if (event.getLevel().getBlockState(event.getPos()).getBlock() instanceof FlowerPotBlock
                && !event.getLevel().getGameRules().getBoolean(ModGameRules.RULE_STARDEW_ALLOW_PUBLIC_BUILDING)
                && !com.stardew.craft.farm.FarmInstanceAllocator.isInFarmInstanceRegion(event.getPos())
                && !com.stardew.craft.greenhouse.GreenhouseManager.isInGreenhouseInterior(
                        event.getLevel(), event.getPos())) {
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.CONSUME);
            return;
        }

        BlockPos targetPos = event.getPos();
        BlockPos placePos = event.getLevel().getBlockState(targetPos).canBeReplaced()
                ? targetPos
                : targetPos.relative(event.getFace());
        net.minecraft.world.item.ItemStack heldItem = event.getItemStack();
        if(event.getEntity() instanceof ServerPlayer actor && (heldItem.getItem() instanceof BlockItem || heldItem.getItem() instanceof net.minecraft.world.item.BucketItem)
                && com.stardew.craft.building.runtime.BuildingProtection.protects(actor.serverLevel(),placePos)) {
            event.setUseItem(TriState.FALSE);denyBuilding(actor);return;
        }
        if (!event.getEntity().isCreative()
                && !event.getLevel().getGameRules().getBoolean(ModGameRules.RULE_STARDEW_ALLOW_PUBLIC_BUILDING)
                && heldItem.getItem() instanceof BlockItem blockItem
                && isKnownPublicPlacementTarget(event.getLevel(), placePos)
                && !isPublicCrabPotItem(blockItem)) {
            // Deny only the held item's use. The clicked public block may still handle its
            // own interaction (for example, a door can open while a block is held).
            event.setUseItem(TriState.FALSE);
            if (event.getEntity() instanceof ServerPlayer player) {
                com.stardew.craft.network.GlobalHudMessagePayload.sendTo(player,
                        Component.translatable(
                                "stardewcraft.farm.build_farm_only"));
            }
            return;
        }

        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.isCreative()) {
            return;
        }
        // 温室内部允许交互
        if (event.getLevel() instanceof ServerLevel sl
                && com.stardew.craft.greenhouse.GreenhouseManager.isInGreenhouseInterior(sl, event.getPos())) {
            if (canModifyGreenhouseAt(player, sl, event.getPos())) {
                return;
            }
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.CONSUME);
            com.stardew.craft.network.GlobalHudMessagePayload.sendTo(player,
                    Component.translatable("stardewcraft.farm.build_farm_only"));
            return;
        }
        // 传送触发方块不受保护（进入别人家的屋内/屋外必须能触发传送）
        if (event.getLevel().getBlockState(event.getPos()).getBlock()
                instanceof com.stardew.craft.block.portal.PortalTriggerBlock) {
            return;
        }
        // 水桶/岩浆桶等流体桶：在任何受保护区域都禁止放置流体
        // （NeoForge 中 BucketItem.emptyContents → LiquidBlock.placeLiquid 不触发 EntityPlaceEvent，
        //  必须在 RightClickBlock 阶段拦截）
        if (heldItem.getItem() instanceof net.minecraft.world.item.BucketItem bucket) {
            // 空桶（拾取流体）允许通过；有内容的桶才做放置保护
            if (bucket.content != net.minecraft.world.level.material.Fluids.EMPTY) {
                if (!canBuildAt(player, placePos)) {
                    event.setCanceled(true);
                    event.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);
                    com.stardew.craft.network.GlobalHudMessagePayload.sendTo(player,
                            Component.translatable("stardewcraft.farm.build_farm_only"));
                    return;
                }
            }
        }
        // 别人的农场禁止右键交互（公共区域允许：开门、献祭、NPC交互等）
        if (isOnProtectedFarm(player, event.getPos())) {
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.CONSUME);
            com.stardew.craft.network.GlobalHudMessagePayload.sendTo(player,
                    Component.translatable("stardewcraft.farm.build_farm_only"));
        }
    }

    private static boolean mayCompletePlacement(
            ServerLevel level,
            ServerPlayer player,
            BlockPos pos,
            net.minecraft.world.level.block.state.BlockState replacedState
    ) {
        if(com.stardew.craft.building.runtime.BuildingProtection.protects(level,pos))return false;
        // Desert.isTilePlaceable: nothing may be placed in the desert merchant's area.
        if (level.dimension() == ModDimensions.STARDEW_VALLEY
                && com.stardew.craft.shop.CamelMerchantEvents.isMerchantZone(pos)) {
            return false;
        }
        if (com.stardew.craft.greenhouse.GreenhouseManager
                .isInGreenhouseExterior(level, pos)) {
            return false;
        }
        if (com.stardew.craft.greenhouse.GreenhouseManager
                .isInGreenhouseInterior(level, pos)) {
            return player != null && canModifyGreenhouseAt(player, level, pos);
        }
        if (isPublicWaterCrabPot(level, pos)) {
            return true;
        }
        if (com.stardew.craft.manager.ArtifactSpotDigService.isSpot(replacedState)) {
            return player == null || com.stardew.craft.manager.ArtifactSpotDigService.allowed(player, pos);
        }
        return player == null
                ? !isProtectedNonFarmArea(level, pos)
                : canBuildAt(player, pos);
    }

    private static boolean isKnownPublicPlacementTarget(
            net.minecraft.world.level.Level level,
            BlockPos pos
    ) {
        return level.dimension() == ModDimensions.STARDEW_VALLEY
                && !com.stardew.craft.interior.FarmCaveRuntime.isCaveRegion(pos)
                && !com.stardew.craft.farm.FarmInstanceAllocator
                        .isInFarmInstanceRegion(pos)
                && !com.stardew.craft.greenhouse.GreenhouseManager
                        .isInGreenhouseInterior(level, pos)
                && !com.stardew.craft.communitycenter.quarry
                        .QuarryAccessManager.isInQuarryArea(pos);
    }

    private static boolean isPublicCrabPotItem(BlockItem blockItem) {
        return blockItem.getBlock() == ModBlocks.CRAB_POT.get();
    }

    public static boolean canModifyGreenhouseAt(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (player == null || level == null || pos == null) {
            return false;
        }
        if (!com.stardew.craft.greenhouse.GreenhouseManager.isInGreenhouseInterior(level, pos)) {
            return false;
        }

        java.util.UUID ownerUUID = PlayerInteriorAllocator.get(level).findGreenhouseOwner(pos);
        if (ownerUUID == null) {
            return true;
        }

        FarmInstance farm = com.stardew.craft.farm.FarmInstanceRegistry.get().getFarm(ownerUUID);
        if (farm != null && farm.isFarmer(player.getUUID())) {
            return true;
        }
        return com.stardew.craft.farm.FarmPermissionManager.get()
                .canModify(ownerUUID, player.getUUID());
    }

    public static boolean isOriginalGreenhouseStructureBlock(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) {
            return false;
        }
        if (!com.stardew.craft.greenhouse.GreenhouseManager.isInGreenhouseInterior(level, pos)) {
            return false;
        }

        BlockPos origin = PlayerInteriorAllocator.get(level).findGreenhouseOrigin(pos);
        if (origin == null) {
            origin = com.stardew.craft.greenhouse.GreenhouseManager.INTERIOR_ORIGIN;
        }

        int rx = pos.getX() - origin.getX();
        int ry = pos.getY() - origin.getY();
        int rz = pos.getZ() - origin.getZ();
        return GreenhouseInteriorCache.get().isOriginalStructureBlock(rx, ry, rz);
    }

    private static boolean isPublicWaterCrabPot(ServerLevel level, BlockPos pos) {
        return FarmAreaResolver.getOwnerAt(pos) == null
                && level.getBlockState(pos).is(ModBlocks.CRAB_POT.get())
                && level.getFluidState(pos).is(net.minecraft.world.level.material.Fluids.WATER);
    }

    private static boolean canAccessCrabPot(ServerLevel level, BlockPos pos, ServerPlayer player) {
        if (level.getBlockEntity(pos) instanceof com.stardew.craft.blockentity.CrabPotBlockEntity crabPot) {
            return crabPot.canAccess(player.getUUID());
        }
        return true;
    }
}
