package com.stardew.craft.interior;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.blockentity.WoodenChestBlockEntity;
import com.stardew.craft.network.payload.StarterChestHintPayload;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.network.ObjectDialogueService;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.warp.ModTeleport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 跨维度传送工具：处理 Overworld ↔ Stardew Valley 巫师塔枢纽传送。
 * <p>
 * <b>Contract:</b> Mod code must teleport players via {@link com.stardew.craft.warp.ModTeleport},
 * not raw {@link ServerPlayer#teleportTo(ServerLevel, double, double, double, float, float)}.
 * Direct calls bypass the {@link #markSkipAutoTeleport} flag and will be redirected
 * to the farm spawn / current mine floor entrance by {@code DimensionEventHandler}
 * (which treats unmarked dim changes as vanilla sources — {@code /tp}, {@code /execute in},
 * respawn, etc.).
 */
@SuppressWarnings({"null", "unused"})
public final class CrossDimensionTeleporter {

    private static final String PLAYER_LAST_PORTAL_TICK = "stardewcraft_last_portal_tick";
    private static final long PORTAL_COOLDOWN_TICKS = 8L;

    /**
     * 跳过 DimensionEventHandler 自动传送的玩家集合。
     * 当 CrossDimensionTeleporter 主动传送玩家到星露谷维度时，
     * DimensionEventHandler 不应再覆盖传送目标。
     */
    private static final Set<UUID> SKIP_AUTO_TELEPORT = ConcurrentHashMap.newKeySet();

    // 巫师塔内部坐标（与 InteriorSubspaceManager 一致）
    private static final BlockPos WIZARD_TOWER_ORIGIN = BlockPos.ZERO;
    private static final BlockPos WIZARD_TOWER_INDOOR_SPAWN_OFFSET = new BlockPos(-178, 34, 63);

    // 星露谷维度的巫师塔室外坐标
    private static final BlockPos STARDEW_WIZARD_OUTDOOR_POS = new BlockPos(-179, 69, 51);

    private CrossDimensionTeleporter() {}

    /**
     * 供 DimensionEventHandler 调用：如果该玩家正在被 CrossDimensionTeleporter
     * 传送，则消耗标记并返回 true，表示不应覆盖传送目标。
     */
    public static boolean consumeSkipAutoTeleport(UUID uuid) {
        return SKIP_AUTO_TELEPORT.remove(uuid);
    }

    /** 外部调用：标记该玩家下次进入星露谷维度时跳过自动传送 */
    public static void markSkipAutoTeleport(UUID uuid) {
        SKIP_AUTO_TELEPORT.add(uuid);
    }

    /**
     * 记录玩家在主世界的位置，供返回时使用。
     */
    public static void overworldToWizardInterior(ServerPlayer player) {
        if (checkCooldown(player)) return;

        // 记录来源信息
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        data.setOverworldReturnPos(player.blockPosition());
        data.setWizardSourceDimension(player.level().dimension());

        // 获取星露谷维度
        ServerLevel stardewLevel = player.server.getLevel(ModDimensions.STARDEW_VALLEY);
        if (stardewLevel == null) {
            StardewCraft.LOGGER.error("[WIZARD] Stardew Valley dimension not found!");
            return;
        }

        // 确保巫师塔内部已加载
        InteriorSubspaceManager.ensureLoaded(stardewLevel, "wizard_overworld_portal");

        BlockPos spawnAbs = WIZARD_TOWER_ORIGIN.offset(WIZARD_TOWER_INDOOR_SPAWN_OFFSET);

        // 传送前清理
        player.closeContainer();
        player.stopUsingItem();

        // 跨维度传送到巫师塔内部 — ModTeleport 自动跳过维度拦截
        ModTeleport.to(player, stardewLevel,
            spawnAbs.getX() + 0.5, spawnAbs.getY(), spawnAbs.getZ() + 0.5,
            180.0F, 0.0F);

        // 设置室内标志 + 夜视
        applyInteriorEnter(player);
        markCooldown(player);

        // 强制触发 NPC 系统初始化，确保巫师立刻出现
        com.stardew.craft.npc.NpcSystem.forceTickNow(stardewLevel);

        StardewCraft.LOGGER.info("[WIZARD] {} teleported from overworld to wizard tower interior", player.getName().getString());
    }

    /**
     * 从巫师塔内部回到主世界。
     * 读取之前记录的主世界坐标。
     */
    public static void wizardInteriorToOverworld(ServerPlayer player) {
        if (checkCooldown(player)) return;

        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        BlockPos returnPos = data.getOverworldReturnPos();

        ServerLevel overworld = player.server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            StardewCraft.LOGGER.error("[WIZARD] Overworld dimension not found!");
            return;
        }

        if (returnPos == null) {
            StardewCraft.LOGGER.warn("[WIZARD] No overworld return pos recorded; canceling return teleport");
            return;
        }

        // 传送前清理
        player.closeContainer();
        player.stopUsingItem();

        ModTeleport.to(player, overworld,
            returnPos.getX() + 0.5, returnPos.getY(), returnPos.getZ() + 0.5,
            player.getYRot(), player.getXRot());

        // 清除室内标志
        applyInteriorExit(player);
        markCooldown(player);

        // 清理来源数据
        data.setWizardSourceDimension(null);

        StardewCraft.LOGGER.info("[WIZARD] {} teleported from wizard tower interior back to overworld at {}", player.getName().getString(), returnPos);
    }

    /**
    * 从巫师塔内部前往星露谷室外。
    * 玩家必须已有农场实例；没有农场时不进入旧公共农场区域。
     */
    public static void wizardInteriorToStardewOutdoor(ServerPlayer player) {
        wizardInteriorToStardewOutdoor(player, false);
    }

    public static void wizardInteriorToStardewOutdoor(ServerPlayer player, boolean giveStarterItemsInInventory) {
        if (checkCooldown(player)) return;

        ServerLevel stardewLevel = player.server.getLevel(ModDimensions.STARDEW_VALLEY);
        if (stardewLevel == null) {
            StardewCraft.LOGGER.error("[WIZARD] Stardew Valley dimension not found!");
            return;
        }

        // 传送前清理
        player.closeContainer();
        player.stopUsingItem();

        com.stardew.craft.farm.FarmInstanceRegistry registry = com.stardew.craft.farm.FarmInstanceRegistry.get();
        com.stardew.craft.farm.FarmInstance farm = registry.getFarmForPlayer(player.getUUID());
        if (farm == null) {
            ObjectDialogueService.show(player, Component.translatable("stardewcraft.farm.create_first"));
            StardewCraft.LOGGER.warn("[WIZARD] Refused Stardew outdoor teleport for {}: no personal farm",
                player.getName().getString());
            return;
        }

        BlockPos spawnTarget = farm.getSpawnPoint();
        StardewCraft.LOGGER.info("[WIZARD] {} teleporting to personal farm spawn at {}",
            player.getName().getString(), spawnTarget);

        ModTeleport.to(player, stardewLevel,
            spawnTarget.getX() + 0.5,
            spawnTarget.getY(),
            spawnTarget.getZ() + 0.5,
            180.0F, 0.0F);

        applyInteriorExit(player);
        markCooldown(player);

        // 首次传送到星露谷：给予新手工具六件套
        giveStarterToolsIfNeeded(player, giveStarterItemsInInventory);

        StardewCraft.LOGGER.info("[WIZARD] {} teleported from wizard tower interior to personal farm spawn", player.getName().getString());
    }

    /**
     * 智能出口：根据玩家来源维度决定传送目标。
     * - 如果来自主世界 → 回到主世界记录坐标
     * - 如果来自星露谷（或来源未知）→ 回到星露谷室外
     */
    public static void wizardInteriorSmartExit(ServerPlayer player) {
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        ResourceKey<Level> sourceDim = data.getWizardSourceDimension();

        if (sourceDim != null && Level.OVERWORLD.equals(sourceDim)) {
            wizardInteriorToOverworld(player);
        } else {
            // 默认行为：回到星露谷室外（与现有 wizard_tower_exit 一致）
            // 不做跨维度传送，走原有 portal 系统处理
            // 这里不需要额外处理，因为原有 exit portal 仍然存在
        }
    }

    // ──── Internal helpers ────

    /**
     * 首次到达星露谷时在玩家面前放置一个装有初始物资的木箱。
     * 箱子面朝玩家，并发送 hint 提示"点击领取初始物资"。
     * 可从外部调用（如农场入口传送后）。
     */
    public static void giveStarterToolsIfNeeded(ServerPlayer player) {
        giveStarterToolsIfNeeded(player, false);
    }

    public static void giveStarterToolsIfNeeded(ServerPlayer player, boolean directToInventory) {
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        if (data.isStarterToolsGiven()) return;

        ItemStack[] starterTools = createStarterTools();

        if (directToInventory) {
            giveStarterItemsToInventory(player, starterTools);
            data.setStarterToolsGiven(true);
            StardewCraft.LOGGER.info("[WIZARD] Granted starter items directly to {}'s inventory", player.getName().getString());
            sendWelcomeAnnouncements(player);
            return;
        }

        ServerLevel level = player.serverLevel();

        // 玩家传送时面朝南（yaw=180），面前一格 = +Z
        BlockPos chestPos = player.blockPosition().relative(Direction.SOUTH);

        // 放置木箱，面朝玩家（即面朝北）
        BlockState chestState = ModBlocks.WOODEN_CHEST.get().defaultBlockState()
                .setValue(com.stardew.craft.block.utility.WoodenChestBlock.FACING, Direction.NORTH);
        level.setBlock(chestPos, chestState, 3);

        // 填充初始物资
        BlockEntity be = level.getBlockEntity(chestPos);
        if (be instanceof WoodenChestBlockEntity chest) {
            for (int i = 0; i < starterTools.length && i < chest.getContainerSize(); i++) {
                chest.setItem(i, starterTools[i]);
            }
            chest.setChanged();
        }

        // 发送 hint 到客户端
        PacketDistributor.sendToPlayer(player, new StarterChestHintPayload(chestPos, true));

        data.setStarterToolsGiven(true);
        StardewCraft.LOGGER.info("[WIZARD] Placed starter chest for {} at {}", player.getName().getString(), chestPos);

        sendWelcomeAnnouncements(player);
    }

    private static ItemStack[] createStarterTools() {
        java.util.List<ItemStack> starterItems = new java.util.ArrayList<>();
        starterItems.add(new ItemStack(ModItems.PICKAXE.get()));
        starterItems.add(new ItemStack(ModItems.AXE.get()));
        starterItems.add(new ItemStack(ModItems.HOE.get()));
        starterItems.add(new ItemStack(ModItems.WATERING_CAN.get()));
        starterItems.add(new ItemStack(ModItems.SCYTHE.get()));
        starterItems.add(new ItemStack(ModItems.PARSNIP_SEEDS.get(), 15));
        starterItems.add(new ItemStack(ModItems.MAILBOX.get()));
        starterItems.add(new ItemStack(ModItems.SHIPPING_BIN.get()));
        starterItems.add(new ItemStack(ModItems.BED_1.get()));

        boolean isWinter = com.stardew.craft.time.StardewTimeManager.get().getCurrentSeason() == 3;
        if (isWinter) {
            starterItems.add(new ItemStack(ModItems.JUNIMO_GREENHOUSE_RUNE.get()));
        }
        return starterItems.toArray(ItemStack[]::new);
    }

    private static void giveStarterItemsToInventory(ServerPlayer player, ItemStack[] starterTools) {
        for (ItemStack starterTool : starterTools) {
            ItemStack remaining = starterTool.copy();
            boolean added = player.getInventory().add(remaining);
            if (!added && !remaining.isEmpty()) {
                player.drop(remaining, false);
            }
        }
        player.inventoryMenu.broadcastChanges();
    }

    private static void sendWelcomeAnnouncements(ServerPlayer player) {

        // 延迟 1 秒发送欢迎公告（让玩家先加载完场景）
        player.server.execute(() ->
            player.server.execute(() -> {
                sendWelcomeAnnouncement(player);
                sendBilibiliAnnouncement(player);
                if (com.stardew.craft.time.StardewTimeManager.get().getCurrentSeason() == 3) {
                    sendRuneAnnouncement(player);
                }
                OrangeSisterWelcomeService.scheduleIfEligible(player);
            })
        );
    }

    /**
     * 首次进入星露谷时发送内测欢迎公告。
     */
    private static void sendWelcomeAnnouncement(ServerPlayer player) {
        player.sendSystemMessage(Component.literal(""));
        player.sendSystemMessage(Component.literal("§6§l═══════════ §e§lStardewCraft §6§l═══════════"));
        player.sendSystemMessage(Component.literal(""));
        player.sendSystemMessage(Component.translatable("stardewcraft.welcome.beta_warning"));
        player.sendSystemMessage(Component.literal(""));
        player.sendSystemMessage(Component.translatable("stardewcraft.welcome.feedback"));
        player.sendSystemMessage(Component.literal(""));
        player.sendSystemMessage(Component.translatable("stardewcraft.welcome.menu_hint"));
        player.sendSystemMessage(Component.translatable("stardewcraft.welcome.intuition_hint"));
        player.sendSystemMessage(Component.literal(""));
        player.sendSystemMessage(Component.literal("§6§l═══════════════════════════════════"));
        player.sendSystemMessage(Component.literal(""));
    }

    /**
     * B 站关注公告：提示玩家点击领取彩虹猫之刃。
     */
    private static void sendBilibiliAnnouncement(ServerPlayer player) {
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        if (data.isBilibiliRewardClaimed()) return;

        MutableComponent clickMsg = Component.translatable("stardewcraft.bilibili.follow_link")
            .setStyle(Style.EMPTY
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/stardew bilibili_claim"))
                .withUnderlined(true)
                .withColor(ChatFormatting.AQUA));
        MutableComponent hint = Component.translatable("stardewcraft.bilibili.gift_hint");

        player.sendSystemMessage(Component.translatable("stardewcraft.bilibili.follow_title"));
        player.sendSystemMessage(Component.literal(""));
        player.sendSystemMessage(Component.translatable("stardewcraft.bilibili.follow_description"));
        player.sendSystemMessage(Component.literal("  ").append(clickMsg).append(hint));
        player.sendSystemMessage(Component.literal(""));
        player.sendSystemMessage(Component.literal("§6§l═══════════════════════════════════"));
        player.sendSystemMessage(Component.literal(""));
    }

    /**
     * 冬季新玩家到达时的温室符文公告。
     */
    private static void sendRuneAnnouncement(ServerPlayer player) {
        player.sendSystemMessage(Component.literal(""));
        player.sendSystemMessage(Component.translatable("stardewcraft.welcome.rune.title"));
        player.sendSystemMessage(Component.literal(""));
        player.sendSystemMessage(Component.translatable("stardewcraft.welcome.rune.line1"));
        player.sendSystemMessage(Component.literal(""));
        player.sendSystemMessage(Component.translatable("stardewcraft.welcome.rune.line2"));
        player.sendSystemMessage(Component.translatable("stardewcraft.welcome.rune.line3"));
        player.sendSystemMessage(Component.translatable("stardewcraft.welcome.rune.line4"));
        player.sendSystemMessage(Component.literal(""));
        player.sendSystemMessage(Component.translatable("stardewcraft.welcome.rune.line5"));
        player.sendSystemMessage(Component.translatable("stardewcraft.welcome.rune.line6"));
        player.sendSystemMessage(Component.translatable("stardewcraft.welcome.rune.line7"));
        player.sendSystemMessage(Component.literal(""));
        player.sendSystemMessage(Component.translatable("stardewcraft.welcome.rune.closing"));
        player.sendSystemMessage(Component.literal(""));
        player.sendSystemMessage(Component.literal("§a═══════════════════════════════════"));
        player.sendSystemMessage(Component.literal(""));
    }

    private static boolean checkCooldown(ServerPlayer player) {
        long now = player.serverLevel().getGameTime();
        long last = player.getPersistentData().getLong(PLAYER_LAST_PORTAL_TICK);
        return now - last < PORTAL_COOLDOWN_TICKS;
    }

    private static void markCooldown(ServerPlayer player) {
        player.getPersistentData().putLong(PLAYER_LAST_PORTAL_TICK, player.serverLevel().getGameTime());
    }

    private static void applyInteriorEnter(ServerPlayer player) {
        com.stardew.craft.event.InteriorPortalInteractionEvents.markInteriorEnter(player);
    }

    private static void applyInteriorExit(ServerPlayer player) {
        com.stardew.craft.event.InteriorPortalInteractionEvents.clearInteriorState(player);
    }
}
