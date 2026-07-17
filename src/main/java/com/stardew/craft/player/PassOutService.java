package com.stardew.craft.player;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.core.ModMiningDimensions;
import com.stardew.craft.core.ModTags;
import com.stardew.craft.item.equipment.StardewBootsItem;
import com.stardew.craft.item.equipment.StardewRingItem;
import com.stardew.craft.item.weapon.StardewWeaponItem;
import com.stardew.craft.network.payload.PassOutPayload;
import com.stardew.craft.warp.ModTeleport;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BedBlock;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 晕倒/死亡核心逻辑。
 * <p>
 * 对标 SDV 原版 Farmer.cs / Game1.cs：
 * <ul>
 *   <li>战斗死亡（矿井 vs 非矿井）→ 金币扣除 + 物品丢失 + 当日救援，不推进日期</li>
 *   <li>2AM 晕倒 → 金币扣除（min(1000, money/10)）</li>
 * </ul>
 * 创造模式对所有惩罚完全豁免。
 */
@SuppressWarnings("null")
public final class PassOutService {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final Random RANDOM = new Random();
    private static final Map<UUID, RescueTarget> PENDING_RESCUE_TARGETS = new ConcurrentHashMap<>();
    private static final RescueTarget DESERT_FESTIVAL_MEDICAL_TENT_RESCUE = new RescueTarget(
        -221.0D, 64.0D, -195.0D, 0.0F, 0.0F
    );

    /**
     * 防止同一 tick 内对同一玩家重复触发 onCombatDeath。
     * 因为 onPlayerHurt（HP→0）和 onPlayerDeath 可能在同一帧先后触发。
     */
    private static final java.util.Map<java.util.UUID, Long> lastKnockoutTick = new java.util.WeakHashMap<>();

    /**
     * 击倒状态：玩家 HP 归零后到传送完成前，完全免疫所有伤害。
     * 防止在持续伤害环境（如岩浆块）中反复死亡导致黑屏卡死。
     */
    private static final java.util.Set<java.util.UUID> knockedOutPlayers = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

    /**
     * 战斗死亡救援后的保护窗口。防止玩家刚被传送回农场时，残留伤害或危险出生点
     * 再次触发同一条死亡链。
     */
    private static final long COMBAT_DEATH_RECOVERY_TICKS = 20L * 20L;
    private static final java.util.Map<java.util.UUID, Long> combatDeathRecoveryUntilTick = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 过夜晕倒的惩罚结果（暂存），由 advanceDayWithSleepTime 消费后合并进 OvernightSettlementPayload。
     * 战斗死亡不写入这里；它是当日个人救援，直接发送 PassOutPayload 给死亡玩家。
     */
    private static final java.util.Map<java.util.UUID, PassOutResult> pendingPassOutResults = new java.util.concurrent.ConcurrentHashMap<>();

    /** 晕倒惩罚结果；settlementDay 是应展示该结果的次日绝对日期。 */
    public record PassOutResult(PassOutType type, int moneyLost,
                                List<net.minecraft.world.item.ItemStack> lostItems,
                                int settlementDay) {
        /** 兼容旧调用方；无日期的结果不会被误用于新的夜间结算。 */
        public PassOutResult(PassOutType type, int moneyLost, List<net.minecraft.world.item.ItemStack> lostItems) {
            this(type, moneyLost, lostItems, Integer.MIN_VALUE);
        }
    }

    /** 消费指定玩家的晕倒结果（一次性），返回 null 表示该玩家未晕倒 */
    @javax.annotation.Nullable
    public static PassOutResult consumePassOutResult(java.util.UUID playerId) {
        return pendingPassOutResults.remove(playerId);
    }

    /** 只消费当前结算日的结果；过期结果直接丢弃，未来结果继续保留。 */
    @javax.annotation.Nullable
    public static PassOutResult consumePassOutResult(java.util.UUID playerId, int settlementDay) {
        PassOutResult result = pendingPassOutResults.get(playerId);
        if (result == null || result.settlementDay() > settlementDay) {
            return null;
        }
        pendingPassOutResults.remove(playerId, result);
        if (result.settlementDay() < settlementDay) {
            LOGGER.warn("[PASSOUT] Discarded stale result for {} (resultDay={}, settlementDay={})",
                playerId, result.settlementDay(), settlementDay);
            return null;
        }
        return result;
    }

    public static boolean hasPendingPassOutResult(java.util.UUID playerId) {
        return pendingPassOutResults.containsKey(playerId);
    }

    /** 检查玩家是否处于击倒状态（正在黑屏过渡中） */
    public static boolean isKnockedOut(ServerPlayer player) {
        return knockedOutPlayers.contains(player.getUUID());
    }

    /** 清除击倒状态（传送完成后调用） */
    public static void clearKnockedOut(ServerPlayer player) {
        knockedOutPlayers.remove(player.getUUID());
    }

    public static boolean isInCombatDeathRecovery(ServerPlayer player) {
        Long untilTick = combatDeathRecoveryUntilTick.get(player.getUUID());
        if (untilTick == null) {
            return false;
        }
        long now = serverTick(player);
        if (now <= untilTick) {
            return true;
        }
        combatDeathRecoveryUntilTick.remove(player.getUUID(), untilTick);
        return false;
    }

    public static void restoreDuringCombatDeathRecovery(ServerPlayer player) {
        clearKnockedOut(player);
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(5.0f);

        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        if (data.getHealth() < data.getMaxHealth()) {
            data.setHealth(data.getMaxHealth());
            PlayerDataEventHandler.syncPlayerData(player, data);
            PlayerDataManager.get().setDirty();
        }
    }

    private PassOutService() {}

    // ──────────────────────────────────────
    //  A/B: 战斗死亡（HP 归零）
    // ──────────────────────────────────────

    /**
     * 由 {@link StardewDamageHooks} 的 KnockoutHandler 调用。
     */
    public static void onCombatDeath(ServerPlayer player, DamageSource source) {
        if (isInCombatDeathRecovery(player)) {
            restoreDuringCombatDeathRecovery(player);
            return;
        }

        // 防重入：同一 tick 不重复处理
        long currentTick = serverTick(player);
        Long lastTick = lastKnockoutTick.get(player.getUUID());
        if (lastTick != null && lastTick == currentTick) {
            return;
        }
        lastKnockoutTick.put(player.getUUID(), currentTick);

        PlayerStardewData data = PlayerDataManager.getPlayerData(player);

        // 创造模式豁免
        if (player.isCreative()) {
            data.setHealth(data.getMaxHealth());
            syncAndSave(player, data);
            return;
        }

        boolean inMine = player.level().dimension() == ModMiningDimensions.STARDEW_MINING;

        // 1. 金币惩罚
        int moneyLost = calcCombatMoneyLoss(data, player, inMine);
        if (moneyLost > 0) {
            PlayerStardewDataAPI.removeMoney(player, moneyLost);
        }

        // 2. 物品丢失
        boolean desertFestivalSkullCavernDeath = com.stardew.craft.festival.desert.DesertFestivalMineService.isInFestivalSkullCavern(player);
        List<ItemStack> lostItems;
        if (desertFestivalSkullCavernDeath) {
            removeDesertFestivalEggPenalty(player);
            lostItems = List.of();
            PENDING_RESCUE_TARGETS.put(player.getUUID(), DESERT_FESTIVAL_MEDICAL_TENT_RESCUE);
        } else {
            lostItems = loseItemsOnDeath(player, data);
            PENDING_RESCUE_TARGETS.remove(player.getUUID());
        }
        data.setItemsLostLastDeath(lostItems);

        // 3. 战斗死亡统计。战斗死亡只救援本人，不推进到次日。
        data.setPassedOutFromCombat(false);
        data.recordCombatDeath();

        // 4. 标记击倒状态（HP 保持 0，免疫后续伤害，直到客户端黑屏完成并 ACK）
        knockedOutPlayers.add(player.getUUID());
        // 不恢复 SDV 血量——保持 0 直到传送后再恢复

        // 5. 安排死亡邮件
        scheduleDeathMail(player, data, inMine, moneyLost, !lostItems.isEmpty());

        PassOutType passOutType = inMine ? PassOutType.COMBAT_MINE : PassOutType.COMBAT_OVERWORLD;

        syncAndSave(player, data);
        PacketDistributor.sendToPlayer(player, new PassOutPayload(passOutType, moneyLost, List.copyOf(lostItems)));

        LOGGER.info("[PASSOUT] {} combat death in {} — lost {}g, {} items",
                player.getName().getString(),
                inMine ? "mine" : "overworld",
                moneyLost, lostItems.size());
    }

    // ──────────────────────────────────────
    //  C: 2:00 AM 晕倒
    // ──────────────────────────────────────

    /**
     * 由 {@link com.stardew.craft.event.DimensionEventHandler} 在 2AM 时调用。
     *
     * @return 扣了多少钱（用于客户端显示）
     */
    public static int on2AMPassOut(ServerPlayer player) {
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);

        // 创造模式豁免
        if (player.isCreative()) {
            return 0;
        }

        boolean nearOwnBed = isNearOwnBed(player, 10);
        int moneyLost = calculateOvernightMoneyLoss(PlayerStardewDataAPI.getMoney(player), nearOwnBed);
        if (moneyLost > 0) {
            PlayerStardewDataAPI.removeMoney(player, moneyLost);
        }

        // 在自己的床边只是没能及时入睡，不需要 NPC 救援。
        if (!nearOwnBed) {
            schedulePassOutMail(data);
        }
        data.record2AmPassOut();

        // 不再发送单独的 PassOutPayload —— 晕倒数据将合并进 OvernightSettlementPayload，
        // 由客户端在结算流程中统一展示（先渐黑→摘要→升级→出货），避免两个画面冲突。
        pendingPassOutResults.put(player.getUUID(),
                new PassOutResult(PassOutType.EXHAUSTION_2AM, moneyLost, List.of(), nextSettlementDay()));

        syncAndSave(player, data);

        LOGGER.info("[PASSOUT] {} 2AM pass out — lost {}g{}",
                player.getName().getString(), moneyLost, nearOwnBed ? " (near own bed)" : "");

        return moneyLost;
    }

    // ──────────────────────────────────────
    //  D: 体力耗尽晕倒（energy ≤ -15）
    // ──────────────────────────────────────

    /**
     * 由 PlayerDataEventHandler.onPlayerTick() 在 energy ≤ -15 时调用。
     * 惩罚同 2AM 晕倒：金币扣除 + 邮件，无物品丢失。
     * 体力耗尽属于过夜晕倒；多人时计入睡眠投票，达到阈值后统一进入夜间结算黑屏。
     */
    public static void onExhaustionPassOut(ServerPlayer player) {
        if (hasPendingPassOutResult(player.getUUID())) {
            return;
        }

        PlayerStardewData data = PlayerDataManager.getPlayerData(player);

        if (player.isCreative()) {
            return;
        }

        boolean nearOwnBed = isNearOwnBed(player, 10);
        int moneyLost = calculateOvernightMoneyLoss(PlayerStardewDataAPI.getMoney(player), nearOwnBed);
        if (moneyLost > 0) {
            PlayerStardewDataAPI.removeMoney(player, moneyLost);
        }

        if (!nearOwnBed) {
            schedulePassOutMail(data);
        }
        data.recordExhaustionPassOut();

        data.setEnergy(0.0F);

        pendingPassOutResults.put(player.getUUID(),
            new PassOutResult(PassOutType.EXHAUSTION_STAMINA, moneyLost, List.of(), nextSettlementDay()));

        syncAndSave(player, data);
        com.stardew.craft.event.DimensionEventHandler.requestPassOutAdvance(player);

        LOGGER.info("[PASSOUT] {} exhaustion pass out — lost {}g{}",
                player.getName().getString(), moneyLost, nearOwnBed ? " (near own bed)" : "");
    }

    static int calculateOvernightMoneyLoss(int money, boolean nearOwnBed) {
        if (nearOwnBed || money <= 0) {
            return 0;
        }
        return Math.min(1000, money / 10);
    }

    private static int nextSettlementDay() {
        return com.stardew.craft.time.StardewTimeManager.get().getAbsoluteDay() + 1;
    }

    /** 查找玩家所属农场内、半径范围中的真实床方块。 */
    private static boolean isNearOwnBed(ServerPlayer player, int radius) {
        if (player.level().dimension() != ModDimensions.STARDEW_VALLEY) {
            return false;
        }
        var farm = com.stardew.craft.farm.FarmInstanceRegistry.get()
            .getFarmForPlayer(player.getUUID());
        if (farm == null || !farm.contains(player.blockPosition())) {
            return false;
        }

        net.minecraft.core.BlockPos center = player.blockPosition();
        int radiusSq = radius * radius;
        for (net.minecraft.core.BlockPos pos : net.minecraft.core.BlockPos.betweenClosed(
                center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {
            if (center.distSqr(pos) > radiusSq || !farm.contains(pos)) {
                continue;
            }
            var state = player.level().getBlockState(pos);
            if (state.getBlock() instanceof BedBlock
                    || state.is(ModBlocks.BED_1.get())
                    || state.is(ModBlocks.BED_2.get())) {
                return true;
            }
        }
        return false;
    }

    // ──────────────────────────────────────
    //  金币计算
    // ──────────────────────────────────────

    private static int calcCombatMoneyLoss(PlayerStardewData data, ServerPlayer player, boolean inMine) {
        int money = PlayerStardewDataAPI.getMoney(player);
        if (money <= 0) return 0;

        int moneyToLose;
        if (inMine) {
            // SDV: rand(Money/40, Money/8), cap 15000
            int lo = money / 40;
            int hi = money / 8;
            if (hi <= lo) {
                moneyToLose = lo;
            } else {
                moneyToLose = lo + RANDOM.nextInt(hi - lo + 1);
            }
            moneyToLose = Math.min(moneyToLose, 15000);

            // 幸运等级减免
            int luckLevel = PlayerStardewDataAPI.getLuckLevel(player);
            moneyToLose -= (int)(luckLevel * 0.01 * moneyToLose);

            // 向下取整到百位
            moneyToLose -= moneyToLose % 100;
        } else {
            // 非矿井：固定上限 1000g
            moneyToLose = Math.min(1000, money);
        }

        return Math.max(0, moneyToLose);
    }

    // ──────────────────────────────────────
    //  物品丢失
    // ──────────────────────────────────────

    private static List<ItemStack> loseItemsOnDeath(ServerPlayer player, PlayerStardewData data) {
        List<ItemStack> lostItems = new ArrayList<>();
        int luckLevel = PlayerStardewDataAPI.getLuckLevel(player);
        double dailyLuck = data.getDailyLuck();
        double itemLossRate = 0.22 - luckLevel * 0.04 - dailyLuck;

        // 从背包末尾向前遍历（SDV 原版行为）
        var inventory = player.getInventory();
        int lost = 0;
        for (int slot = inventory.getContainerSize() - 1; slot >= 0; slot--) {
            if (lost >= 3) break;
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) continue;
            if (!canBeLostOnDeath(stack)) continue;
            if (RANDOM.nextDouble() < itemLossRate) {
                lostItems.add(stack.copy());
                inventory.setItem(slot, ItemStack.EMPTY);
                lost++;
            }
        }
        return lostItems;
    }

    private static void removeDesertFestivalEggPenalty(ServerPlayer player) {
        if (!(player.level() instanceof net.minecraft.server.level.ServerLevel level)) {
            return;
        }
        int eggCount = player.getInventory().countItem(com.stardew.craft.item.ModItems.CALICO_EGG.get());
        if (eggCount <= 0) {
            return;
        }
        float percent = com.stardew.craft.festival.desert.DesertFestivalMineService.thinShellsActive(level) ? 0.5F : 0.2F;
        int remaining = Math.max(0, (int)(eggCount * percent));
        for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(com.stardew.craft.item.ModItems.CALICO_EGG.get())) {
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
            }
        }
    }

    private static void grantCombatDeathRecovery(ServerPlayer player) {
        combatDeathRecoveryUntilTick.put(player.getUUID(), serverTick(player) + COMBAT_DEATH_RECOVERY_TICKS);
    }

    private static long serverTick(ServerPlayer player) {
        return player.server.getTickCount();
    }

    /**
     * SDV 原版：工具、武器、戒指、靴子不可丢失；
     * 非星露谷物品不参与丢失；标记 prevent_loss_on_death 的物品不可丢失。
     */
    private static boolean canBeLostOnDeath(ItemStack stack) {
        var item = stack.getItem();
        // 只有 stardewcraft 命名空间的物品可以丢失
        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
        if (!StardewCraft.MODID.equals(id.getNamespace())) return false;
        // 数据标签黑名单（SDV prevent_loss_on_death 等价）
        if (stack.is(ModTags.Items.PREVENT_LOSS_ON_DEATH)) return false;
        // 星露谷工具（锄头、斧头、镐、浇水壶、钓竿、镰刀、淘金盘）
        if (item instanceof net.minecraft.world.item.TieredItem) return false;
        if (item instanceof com.stardew.craft.item.tool.HoeItem) return false;
        if (item instanceof com.stardew.craft.item.tool.WateringCanItem) return false;
        if (item instanceof com.stardew.craft.item.tool.FishingRodItem) return false;
        if (item instanceof com.stardew.craft.item.tool.ScytheItem) return false;
        if (item instanceof com.stardew.craft.item.tool.PanItem) return false;
        // 武器（剑、匕首、棍棒）
        if (item instanceof StardewWeaponItem) return false;
        if (item instanceof com.stardew.craft.item.weapon.StardewDaggerItem) return false;
        if (item instanceof com.stardew.craft.item.weapon.StardewClubItem) return false;
        // 戒指
        if (item instanceof StardewRingItem) return false;
        // 靴子
        if (item instanceof StardewBootsItem) return false;
        return true;
    }

    // ──────────────────────────────────────
    //  邮件安排
    // ──────────────────────────────────────

    private static final String[] MINE_DEATH_MAILS = {
            "mineDeath_Robin", "mineDeath_Clint", "mineDeath_Maru", "mineDeath_Linus"
    };

    private static void scheduleDeathMail(ServerPlayer player, PlayerStardewData data,
                                          boolean inMine, int moneyLost, boolean itemsLost) {
        if (inMine) {
            // 随机选一个矿井救援邮件
            String mailId = MINE_DEATH_MAILS[RANDOM.nextInt(MINE_DEATH_MAILS.length)];
            data.addMailForTomorrow(mailId);
        } else {
            // Harvey 诊所邮件
            data.addMailForTomorrow("hospitalDeath");
        }

        // Marlon 物品找回邮件
        if (itemsLost) {
            data.addMailForTomorrow("marlonRecovery");
        }
    }

    private static void schedulePassOutMail(PlayerStardewData data) {
        // 简化版：Linus 40%, Harvey 40%, Marlon 20%
        double roll = RANDOM.nextDouble();
        String mailId;
        if (roll < 0.4) {
            mailId = "passedOut_Linus";
        } else if (roll < 0.8) {
            mailId = "passedOut_Harvey";
        } else {
            mailId = "passedOut_Marlon";
        }
        data.addMailForTomorrow(mailId);
    }

    // ──────────────────────────────────────
    //  次日体力覆盖（由 StardewTimeManager 调用）
    // ──────────────────────────────────────

    /**
     * 旧流程兼容：如果存档里还留有战斗死亡过夜标志，在 sleep() 恢复能量之后压到 2 并清除。
     */
    public static void applyCombatDeathEnergyPenalty(ServerPlayer player) {
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        if (data.isPassedOutFromCombat()) {
            data.setEnergy(Math.min(data.getEnergy(), 2.0f));
            data.setPassedOutFromCombat(false);
        }
    }

    // ──────────────────────────────────────
    //  传送回农场
    // ──────────────────────────────────────

    public static void teleportAfterPassOutAck(ServerPlayer player) {
        RescueTarget rescueTarget = PENDING_RESCUE_TARGETS.remove(player.getUUID());
        if (rescueTarget != null && teleportToRescueTarget(player, rescueTarget)) {
            completeCombatDeathRescue(player);
            return;
        }
        teleportToFarmSpawn(player);
    }

    /**
     * 将玩家传送回星露谷维度农场出生点。
     */
    public static void teleportToFarmSpawn(ServerPlayer player) {
        var server = player.server;
        var stardewLevel = server.getLevel(ModDimensions.STARDEW_VALLEY);
        if (stardewLevel == null) return;

        // 查询玩家的农场出生点
        com.stardew.craft.farm.FarmInstanceRegistry registry = com.stardew.craft.farm.FarmInstanceRegistry.get();
        net.minecraft.core.BlockPos spawnPos = registry.getFarmSpawnPoint(player.getUUID());
        if (spawnPos == null) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("stardewcraft.warp.farm.unavailable"), true);
            LOGGER.warn("[PASS_OUT] Player {} has no farm spawn; skipping farm return teleport.",
                player.getName().getString());
            grantCombatDeathRecovery(player);
            restoreDuringCombatDeathRecovery(player);
            return;
        }

        double sx = spawnPos.getX() + 0.5;
        double sy = spawnPos.getY();
        double sz = spawnPos.getZ() + 0.5;

        ModTeleport.to(player, stardewLevel, sx, sy, sz, player.getYRot(), player.getXRot());
        completeCombatDeathRescue(player);
    }

    private static boolean teleportToRescueTarget(ServerPlayer player, RescueTarget target) {
        var server = player.server;
        var stardewLevel = server.getLevel(ModDimensions.STARDEW_VALLEY);
        if (stardewLevel == null) return false;
        ModTeleport.to(player, stardewLevel, target.x(), target.y(), target.z(), target.yaw(), target.pitch());
        return true;
    }

    private static void completeCombatDeathRescue(ServerPlayer player) {
        // 传送完成：清除击倒状态，恢复满血
        grantCombatDeathRecovery(player);
        clearKnockedOut(player);
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        data.setHealth(data.getMaxHealth());
        PlayerDataEventHandler.syncPlayerData(player, data);
        PlayerDataManager.get().setDirty();

        // 重置矿井层数到 0（SDV 原版：晕倒后回到大厅）
        com.stardew.craft.mining.MiningPlayerData miningData =
            com.stardew.craft.mining.MiningDataManager.getPlayerData(player);
        miningData.setCurrentFloor(0);
        com.stardew.craft.mining.MiningDataManager.savePlayerData(player, miningData);
    }

    private record RescueTarget(double x, double y, double z, float yaw, float pitch) {}

    // ──────────────────────────────────────
    //  工具方法
    // ──────────────────────────────────────

    private static void syncAndSave(ServerPlayer player, PlayerStardewData data) {
        PlayerDataEventHandler.syncPlayerData(player, data);
        PlayerDataManager.get().setDirty();
    }

    /**
     * 晕倒类型枚举。
     */
    public enum PassOutType {
        COMBAT_MINE(0),
        COMBAT_OVERWORLD(1),
        EXHAUSTION_2AM(2),
        EXHAUSTION_STAMINA(3);

        private final int id;
        PassOutType(int id) { this.id = id; }
        public int getId() { return id; }

        public static PassOutType fromId(int id) {
            return switch (id) {
                case 0 -> COMBAT_MINE;
                case 1 -> COMBAT_OVERWORLD;
                case 2 -> EXHAUSTION_2AM;
                case 3 -> EXHAUSTION_STAMINA;
                default -> EXHAUSTION_2AM;
            };
        }
    }
}
