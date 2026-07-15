package com.stardew.craft.event;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.core.FarmAreaResolver;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.entity.animal.BaseCoopAnimalEntity;
import com.stardew.craft.entity.monster.LuckyPurpleShortsMonsterEntity;
import com.stardew.craft.entity.junimo.JunimoEntity;
import com.stardew.craft.entity.npc.BooksellerEntity;
import com.stardew.craft.entity.npc.CamelMerchantEntity;
import com.stardew.craft.entity.npc.StardewNpcEntity;
import com.stardew.craft.entity.npc.TravelingCartEntity;
import com.stardew.craft.farm.FarmInstance;
import com.stardew.craft.festival.FairFestivalService;
import com.stardew.craft.greenhouse.GreenhouseInteriorCache;
import com.stardew.craft.interior.PlayerInteriorAllocator;
import com.stardew.craft.manager.CoalForestArea;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/**
 * 农场区域保护：
 * - 非农场区域（公共区域）不可放置/破坏方块
 * - 别人的农场：无权限(0)不可进入，仅访问权限(1)不可修改方块，完全权限(2)可操作
 * - 自己的农场：完全权限
 * - 创造模式不受限
 */
@EventBusSubscriber(modid = StardewCraft.MODID)
public class FarmAreaProtectionEvents {

    /**
     * 破坏方块：BreakEvent 取消安全（方块不会被破坏，无物品丢失）。
     */
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
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
                player.displayClientMessage(
                        Component.translatable("stardewcraft.farm.build_farm_only"), true);
            } else if (isOriginalGreenhouseStructureBlock(level, event.getPos())) {
                event.setCanceled(true);
            }
            return;
        }
        if (event.getState().is(ModBlocks.CRAB_POT.get()) && !canAccessCrabPot(level, event.getPos(), player)) {
            event.setCanceled(true);
            player.displayClientMessage(Component.translatable("message.stardew_craft.crab_pot.not_owner"), true);
            return;
        }
        if (isPublicWaterCrabPot(level, event.getPos())) {
            return;
        }
        if (!canModifyAt(player, event.getPos())) {
            event.setCanceled(true);
            player.displayClientMessage(
                    Component.translatable("stardewcraft.farm.build_farm_only"), true);
        }
    }

    /**
     * 放置方块：EntityPlaceEvent 在方块已放置后触发。
     * 不取消事件（取消会丢物品），而是立即 destroyBlock 使其掉落回去。
     * 注意：斧头去皮/除锈、锄头锄地等工具交互也会触发此事件（方块替换），
     * 此时 replacedBlock 不是空气，应放行。
     */
    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
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
        // 方块替换（去皮、除锈、锄地等工具交互）→ 也需要权限检查
        // 但远古斑点允许在任何区域被锄（挖掘后变耕地，第二天复原）
        BlockPos pos = event.getPos();
        if (com.stardew.craft.greenhouse.GreenhouseManager.isInGreenhouseExterior(level, pos)) {
            event.getBlockSnapshot().restore();
            return;
        }
        if (com.stardew.craft.greenhouse.GreenhouseManager.isInGreenhouseInterior(level, pos)) {
            if (canModifyGreenhouseAt(player, level, pos)) {
                return;
            }
            event.getBlockSnapshot().restore();
            player.displayClientMessage(
                    Component.translatable("stardewcraft.farm.build_farm_only"), true);
            return;
        }
        if (isPublicWaterCrabPot(level, pos)) {
            return;
        }
        if (!event.getBlockSnapshot().getState().isAir()) {
            if (event.getBlockSnapshot().getState().is(ModBlocks.ARTIFACT_SPOT_DIRT.get())
                    || event.getBlockSnapshot().getState().is(ModBlocks.DESERT_ARTIFACT_SPOT.get())
                    || event.getBlockSnapshot().getState().is(ModBlocks.BEACH_ARTIFACT_SPOT.get())) {
                // 远古斑点（含沙漠/海滩变体）：放行，不受区域保护，避免锄完被回滚
                return;
            }
            if (!canModifyAt(player, pos)) {
                // 还原为原来的方块
                event.getBlockSnapshot().restore();
                player.displayClientMessage(
                        Component.translatable("stardewcraft.farm.build_farm_only"), true);
            }
            return;
        }
        // 温室外观区域不可放置
        if (com.stardew.craft.greenhouse.GreenhouseManager.isInGreenhouseExterior(level, pos)) {
            level.destroyBlock(pos, true, player);
            return;
        }
        // 温室内部区域可自由操作
        if (com.stardew.craft.greenhouse.GreenhouseManager.isInGreenhouseInterior(level, pos)) {
            return;
        }
        if (!canModifyAt(player, pos)) {
            level.destroyBlock(pos, true, player);
            player.displayClientMessage(
                    Component.translatable("stardewcraft.farm.build_farm_only"), true);
        }
    }

    /**
     * 判断玩家是否可以在指定位置修改方块。
     * - 非农场区域 → 不可以
     * - 自己的实例化农场 → 可以
     * - 别人的实例化农场 → 需要 PERM_FULL(2)
     *
     * 可被外部调用（镰刀、作物交互、工具等自定义逻辑需要统一权限检查）。
     */
    public static boolean canModifyAt(ServerPlayer player, BlockPos pos) {
        // 采石场区域：所有玩家都可以挖掘/放置（非农场但属于公共可操作区）
        if (com.stardew.craft.communitycenter.quarry.QuarryAccessManager.isInQuarryArea(pos)) {
            return true;
        }
        // 查找该位置属于哪个农场
        java.util.UUID ownerUUID = FarmAreaResolver.getOwnerAt(pos);
        if (ownerUUID == null) return false; // 不在任何农场内

        // 自己的农场（owner 或 member）
        FarmInstance farm = com.stardew.craft.farm.FarmInstanceRegistry.get().getFarm(ownerUUID);
        if (farm != null && farm.isFarmer(player.getUUID())) return true;

        // 别人的农场：检查权限
        return com.stardew.craft.farm.FarmPermissionManager.get()
                .canModify(ownerUUID, player.getUUID());
    }

    /** Permission gate for entity-backed decorations which do not emit BlockEvent placement/break events. */
    public static boolean canModifyDecorationAt(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (player.isCreative() || level.dimension() != ModDimensions.STARDEW_VALLEY) {
            return true;
        }
        if (com.stardew.craft.greenhouse.GreenhouseManager.isInGreenhouseExterior(level, pos)) {
            return false;
        }
        if (com.stardew.craft.greenhouse.GreenhouseManager.isInGreenhouseInterior(level, pos)) {
            return canModifyGreenhouseAt(player, level, pos);
        }
        return canModifyAt(player, pos);
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
     * 而 canModifyAt 对非农场区域返回 false（禁止修改方块）。
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
        if (event.getState().is(Blocks.GRASS_BLOCK)) {
            event.setCanceled(true);
            return;
        }

        // 公共主区域的普通黄土不允许直接锄成耕地；只有远古斑点黄土允许挖。
        if (event.getState().is(ModBlocks.YELLOW_DIRT.get())
                && FarmAreaResolver.isInStardewButNotFarm(level, event.getPos())
                && !com.stardew.craft.greenhouse.GreenhouseManager.isInGreenhouseInterior(level, event.getPos())) {
            event.setCanceled(true);
            if (event.getPlayer() instanceof ServerPlayer player) {
                player.displayClientMessage(
                        Component.translatable("stardewcraft.farm.build_farm_only"), true);
            }
            return;
        }

        if (event.getPlayer() instanceof ServerPlayer player
            && com.stardew.craft.greenhouse.GreenhouseManager.isInGreenhouseInterior(level, event.getPos())
            && !canModifyGreenhouseAt(player, level, event.getPos())) {
            event.setCanceled(true);
            player.displayClientMessage(
                Component.translatable("stardewcraft.farm.build_farm_only"), true);
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

    @SubscribeEvent
    public static void onBlockMultiPlace(BlockEvent.EntityMultiPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (level.dimension() != ModDimensions.STARDEW_VALLEY) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player && player.isCreative()) {
            return;
        }

        boolean denied = false;
        for (var snapshot : event.getReplacedBlockSnapshots()) {
            BlockPos pos = snapshot.getPos();
            if (event.getEntity() instanceof ServerPlayer player) {
                if (com.stardew.craft.greenhouse.GreenhouseManager.isInGreenhouseInterior(level, pos)) {
                    if (canModifyGreenhouseAt(player, level, pos)) {
                        continue;
                    }
                } else if (canModifyAt(player, pos)) {
                    continue;
                }
            } else if (!isProtectedNonFarmArea(level, pos)) {
                continue;
            }

            snapshot.restore();
            denied = true;
        }

        if (denied && event.getEntity() instanceof ServerPlayer player) {
            player.displayClientMessage(
                    Component.translatable("stardewcraft.farm.build_farm_only"), true);
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

    @SubscribeEvent
    public static void onProtectedNonFarmMobJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!(event.getEntity() instanceof Mob mob)) {
            return;
        }
        if (mob instanceof StardewNpcEntity || mob instanceof JunimoEntity || mob instanceof BooksellerEntity
            || mob instanceof CamelMerchantEntity || mob instanceof TravelingCartEntity) {
            return;
        }
        if (mob instanceof LuckyPurpleShortsMonsterEntity || mob.getTags().contains(LuckyPurpleShortsMonsterEntity.TAG_MARKER)) {
            return;
        }
        if (mob instanceof BaseCoopAnimalEntity animal
            && (animal.getTags().contains(FairFestivalService.FAIR_ANIMAL_MARKER_TAG)
                || animal.getPersistentData().getBoolean(FairFestivalService.FAIR_ANIMAL_PERSISTENT_FLAG))) {
            return;
        }
        if (mob.getTags().contains("stardewcraft_spirit_eve_monster")
            || mob.getPersistentData().getBoolean("stardewcraft_spirit_eve_monster")) {
            return;
        }
        if (mob.getPersistentData().getBoolean("StardewTrinketParrot")) {
            return;
        }
        if (!isProtectedNonFarmArea(level, mob.blockPosition())) {
            return;
        }

        event.setCanceled(true);
        if (mob.isAlive()) {
            mob.discard();
        }
    }

    // ═══════════════════════════════════════════════════════════
    // 右键交互保护：阻止在别人农场上使用机器、箱子、收割作物等
    // ═══════════════════════════════════════════════════════════

    /**
     * 右键方块：阻止在别人农场上进行任何方块交互。
     * 覆盖所有 useWithoutItem / useItemOn / item.useOn 的调用。
     */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.level().dimension() != ModDimensions.STARDEW_VALLEY) {
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
            player.displayClientMessage(
                    Component.translatable("stardewcraft.farm.build_farm_only"), true);
            return;
        }
        // 传送触发方块不受保护（进入别人家的屋内/屋外必须能触发传送）
        if (event.getLevel().getBlockState(event.getPos()).getBlock()
                instanceof com.stardew.craft.block.portal.PortalTriggerBlock) {
            return;
        }
        // Public flower pots are fixed map decoration. Never let players swap
        // their contents; farm-owned pots remain usable and protected by the
        // normal farm permission gate below.
        if (event.getLevel().getBlockState(event.getPos()).getBlock() instanceof FlowerPotBlock
                && FarmAreaResolver.getOwnerAt(event.getPos()) == null) {
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.CONSUME);
            return;
        }
        // 水桶/岩浆桶等流体桶：在任何受保护区域都禁止放置流体
        // （NeoForge 中 BucketItem.emptyContents → LiquidBlock.placeLiquid 不触发 EntityPlaceEvent，
        //  必须在 RightClickBlock 阶段拦截）
        net.minecraft.world.item.ItemStack heldItem = event.getItemStack();
        if (heldItem.getItem() instanceof net.minecraft.world.item.BucketItem bucket) {
            // 空桶（拾取流体）允许通过；有内容的桶才做放置保护
            if (bucket.content != net.minecraft.world.level.material.Fluids.EMPTY) {
                // 流体会放在目标方块的面朝向相邻位置（若目标不可替换）或目标位置
                BlockPos targetPos = event.getPos();
                BlockPos placePos = event.getLevel().getBlockState(targetPos).canBeReplaced()
                        ? targetPos
                        : targetPos.relative(event.getFace());
                if (!canModifyAt(player, placePos)) {
                    event.setCanceled(true);
                    event.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);
                    player.displayClientMessage(
                            Component.translatable("stardewcraft.farm.build_farm_only"), true);
                    return;
                }
            }
        }
        // 别人的农场禁止右键交互（公共区域允许：开门、献祭、NPC交互等）
        if (isOnProtectedFarm(player, event.getPos())) {
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.CONSUME);
            player.displayClientMessage(
                    Component.translatable("stardewcraft.farm.build_farm_only"), true);
        }
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
