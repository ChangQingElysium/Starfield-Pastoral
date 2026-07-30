package com.stardew.craft.player;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.core.ModMiningDimensions;
import com.stardew.craft.core.ModTags;
import com.stardew.craft.cutscene.server.CombatRescueCutsceneCoordinator;
import com.stardew.craft.item.equipment.StardewBootsItem;
import com.stardew.craft.item.equipment.StardewRingItem;
import com.stardew.craft.item.weapon.StardewWeaponItem;
import com.stardew.craft.network.payload.CombatRescueOutcomePayload;
import com.stardew.craft.network.payload.PassOutPayload;
import com.stardew.craft.warp.ModTeleport;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BedBlock;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

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

    /**
     * 防止同一 tick 内对同一玩家重复触发 onCombatDeath。
     * 因为 onPlayerHurt（HP→0）和 onPlayerDeath 可能在同一帧先后触发。
     */
    private static final java.util.Map<java.util.UUID, Long> lastKnockoutTick = new java.util.WeakHashMap<>();

    /**
     * 战斗死亡救援后的保护窗口。防止玩家刚被传送回农场时，残留伤害或危险出生点
     * 再次触发同一条死亡链。
     */
    private static final long COMBAT_DEATH_RECOVERY_TICKS = 20L * 20L;
    private static final int COMBAT_COLLAPSE_TICKS = 20 * 8;
    private static final java.util.Map<java.util.UUID, Long> combatDeathRecoveryUntilTick = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<java.util.UUID, Integer> combatCollapseNotBeforeTick =
            new java.util.concurrent.ConcurrentHashMap<>();

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
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return null;
        }
        PassOutRecoveryData recovery = PassOutRecoveryData.get(server);
        PassOutRecoveryData.Entry entry = recovery.get(playerId);
        if (entry == null || entry.combat()) {
            return null;
        }
        recovery.remove(playerId);
        return new PassOutResult(
                entry.type(), entry.moneyLost(), entry.lostItems(), entry.settlementDay());
    }

    /** 只消费当前结算日的结果；过期结果直接丢弃，未来结果继续保留。 */
    @javax.annotation.Nullable
    public static PassOutResult consumePassOutResult(java.util.UUID playerId, int settlementDay) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return null;
        }
        PassOutRecoveryData.Entry entry =
                PassOutRecoveryData.get(server).consumeOvernight(playerId, settlementDay);
        return entry == null
                ? null
                : new PassOutResult(
                        entry.type(), entry.moneyLost(), entry.lostItems(), entry.settlementDay());
    }

    public static boolean hasPendingPassOutResult(java.util.UUID playerId) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return false;
        }
        PassOutRecoveryData.Entry entry = PassOutRecoveryData.get(server).get(playerId);
        return entry != null && !entry.combat();
    }

    /** True for either an overnight collapse or a combat-rescue transaction. */
    public static boolean hasPendingRecovery(java.util.UUID playerId) {
        var server = ServerLifecycleHooks.getCurrentServer();
        return server != null && PassOutRecoveryData.get(server).get(playerId) != null;
    }

    /** Replays a same-day overnight collapse which was interrupted by logout. */
    public static void resumePendingOvernight(ServerPlayer player) {
        PassOutRecoveryData.Entry entry =
                PassOutRecoveryData.get(player.server).get(player.getUUID());
        if (entry == null || entry.combat()) {
            return;
        }
        int currentDay =
                com.stardew.craft.time.StardewTimeManager.get().getAbsoluteDay();
        if (entry.settlementDay() <= currentDay) {
            // Defensive migration for an offline player whose target day has
            // already arrived without an online settlement payload.
            teleportToFarmSpawn(player);
            PlayerStardewData data = PlayerDataManager.getPlayerData(player);
            data.sleep(1560);
            data.setHealth(data.getMaxHealth());
            PassOutRecoveryData.get(player.server).remove(player.getUUID());
            syncAndSave(player, data);
            return;
        }

        PacketDistributor.sendToPlayer(
                player,
                new com.stardew.craft.network.overnight.OvernightCollapseStartPayload(
                        entry.settlementDay(),
                        entry.type() == PassOutType.EXHAUSTION_STAMINA
                                ? com.stardew.craft.network.overnight
                                        .OvernightCollapseStartPayload.Cause.STAMINA
                                : com.stardew.craft.network.overnight
                                        .OvernightCollapseStartPayload.Cause.TWO_AM));
        // Rebuild the non-durable multiplayer readiness vote and reuse the
        // normal animation-aware transition/return-to-bed scheduling.
        com.stardew.craft.event.DimensionEventHandler.requestPassOutAdvance(player);
    }

    /** 检查玩家是否处于击倒状态（正在黑屏过渡中） */
    public static boolean isKnockedOut(ServerPlayer player) {
        return player != null
                && PassOutRecoveryData.get(player.server).hasCombat(player.getUUID());
    }

    /**
     * Legacy compatibility hook. A durable combat transaction is cleared only
     * after its rescue cutscene/outcome has completed.
     */
    public static void clearKnockedOut(ServerPlayer player) {
        // Deliberately no-op.
    }

    /** Clears process-local timing guards when an integrated/dedicated server stops. */
    public static void clearRuntimeState() {
        lastKnockoutTick.clear();
        combatCollapseNotBeforeTick.clear();
        combatDeathRecoveryUntilTick.clear();
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
        // Keep only Minecraft's entity-health layer alive during the rescue
        // protection window. The authoritative Stardew values were restored
        // to 10 health / at most 2 energy by the completed transaction and
        // must not be silently healed to full here.
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
        PassOutRecoveryData recovery = PassOutRecoveryData.get(player.server);
        PassOutRecoveryData.Entry existing = recovery.get(player.getUUID());
        if (existing != null) {
            if (existing.combat()) {
                resumePending(player);
            }
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
        boolean desertFestivalSkullCavernDeath = com.stardew.craft.festival.desert.DesertFestivalMineService.isInFestivalSkullCavern(player);
        PassOutType passOutType = desertFestivalSkullCavernDeath
                ? PassOutType.COMBAT_DESERT_FESTIVAL
                : (inMine ? PassOutType.COMBAT_MINE : PassOutType.COMBAT_OVERWORLD);

        // Desert Festival is the one original branch which removes its event
        // currency during the collapse. Mine/hospital money and item loss is
        // deliberately deferred until the rescue dialogue reaches its end.
        List<ItemStack> immediateLostItems = List.of();
        if (desertFestivalSkullCavernDeath) {
            immediateLostItems = removeDesertFestivalEggPenalty(player);
        }
        data.setItemsLostLastDeath(immediateLostItems);

        // Combat knockouts rescue only this player and never advance the day.
        data.setPassedOutFromCombat(false);
        data.recordCombatDeath();
        data.setHealth(0);

        long transactionId = ThreadLocalRandom.current().nextLong(1L, Long.MAX_VALUE);
        PassOutRecoveryData.Entry transaction = new PassOutRecoveryData.Entry(
                transactionId,
                passOutType,
                true,
                PassOutRecoveryData.Stage.COLLAPSING,
                0,
                immediateLostItems,
                Integer.MIN_VALUE,
                "",
                ""
        );
        if (passOutType == PassOutType.COMBAT_MINE) {
            CombatRescueCutsceneCoordinator.MineRescueChoice choice =
                    CombatRescueCutsceneCoordinator.selectVanillaMineRescuer(
                            ThreadLocalRandom.current(), null, false, data.isMale());
            transaction.setRescuer(choice.npcId(), choice.dialogue().name());
        }
        recovery.put(player.getUUID(), transaction);

        syncAndSave(player, data);
        PacketDistributor.sendToPlayer(player, new PassOutPayload(
                transactionId, passOutType, 0, immediateLostItems));
        scheduleCombatCollapseFallback(player, transactionId);

        LOGGER.info("[PASSOUT] {} combat knockout in {} — transaction {}",
                player.getName().getString(),
                passOutType,
                transactionId);
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
        if (hasPendingRecovery(player.getUUID())
                || player.isCreative()
                || player.isSpectator()
                || !com.stardew.craft.farm.FarmInstanceRegistry.get().hasFarm(player.getUUID())) {
            return 0;
        }

        int settlementDay = nextSettlementDay();
        PacketDistributor.sendToPlayer(
                player,
                new com.stardew.craft.network.overnight.OvernightCollapseStartPayload(
                        settlementDay,
                        com.stardew.craft.network.overnight.OvernightCollapseStartPayload.Cause.TWO_AM
                )
        );

        boolean passOutSafe = isPassOutSafe(player);
        OvernightPenalty penalty = createOvernightPenalty(player, passOutSafe);
        int moneyLost = penalty.moneyLost();
        if (moneyLost > 0) {
            PlayerStardewDataAPI.removeMoney(player, moneyLost);
        }

        if (penalty.mailId() != null) {
            data.addMailForTomorrow(penalty.mailId());
        }
        data.record2AmPassOut();

        // 不发送单独的 PassOutPayload：原版过夜链没有罚款摘要页，
        // 次日由救援邮件说明损失，再正常进入升级/出货/保存菜单。
        PassOutRecoveryData.get(player.server).put(
                player.getUUID(),
                new PassOutRecoveryData.Entry(
                        ThreadLocalRandom.current().nextLong(1L, Long.MAX_VALUE),
                        PassOutType.EXHAUSTION_2AM,
                        false,
                        PassOutRecoveryData.Stage.AWAITING_OVERNIGHT,
                        moneyLost,
                        List.of(),
                        settlementDay,
                        "",
                        ""
                ));

        syncAndSave(player, data);

        LOGGER.info("[PASSOUT] {} 2AM pass out — lost {}g{}",
                player.getName().getString(), moneyLost, passOutSafe ? " (safe location)" : "");

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
        if (hasPendingRecovery(player.getUUID())) {
            return;
        }

        PlayerStardewData data = PlayerDataManager.getPlayerData(player);

        if (player.isCreative()
                || player.isSpectator()
                || !com.stardew.craft.farm.FarmInstanceRegistry.get()
                        .hasFarm(player.getUUID())) {
            return;
        }

        int settlementDay = nextSettlementDay();
        PacketDistributor.sendToPlayer(
                player,
                new com.stardew.craft.network.overnight.OvernightCollapseStartPayload(
                        settlementDay,
                        com.stardew.craft.network.overnight.OvernightCollapseStartPayload.Cause.STAMINA
                )
        );

        boolean passOutSafe = isPassOutSafe(player);
        OvernightPenalty penalty = createOvernightPenalty(player, passOutSafe);
        int moneyLost = penalty.moneyLost();
        if (moneyLost > 0) {
            PlayerStardewDataAPI.removeMoney(player, moneyLost);
        }

        if (penalty.mailId() != null) {
            data.addMailForTomorrow(penalty.mailId());
        }
        data.recordExhaustionPassOut();

        data.setEnergy(0.0F);

        PassOutRecoveryData.get(player.server).put(
                player.getUUID(),
                new PassOutRecoveryData.Entry(
                        ThreadLocalRandom.current().nextLong(1L, Long.MAX_VALUE),
                        PassOutType.EXHAUSTION_STAMINA,
                        false,
                        PassOutRecoveryData.Stage.AWAITING_OVERNIGHT,
                        moneyLost,
                        List.of(),
                        settlementDay,
                        "",
                        ""
                ));

        syncAndSave(player, data);
        com.stardew.craft.event.DimensionEventHandler.requestPassOutAdvance(player);

        LOGGER.info("[PASSOUT] {} exhaustion pass out — lost {}g{}",
                player.getName().getString(), moneyLost, passOutSafe ? " (safe location)" : "");
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

    /**
     * Original safe locations are FarmHouse/IslandFarmHouse/Cellar or maps
     * with PassOutSafe. Dynamic farm interiors are not yet separate logical
     * locations in this project, so a real bed on the player's own farm is
     * retained as the compatibility fallback.
     */
    private static boolean isPassOutSafe(ServerPlayer player) {
        net.minecraft.resources.ResourceLocation passOutSafeProperty =
                ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "pass_out_safe");
        boolean explicitSafe = com.stardew.craft.api.v1.world.StardewLocations
                .find(player.level(), player.blockPosition())
                .map(location -> {
                    String path = location.id().getPath();
                    return location.property(passOutSafeProperty)
                            .map(Boolean::parseBoolean)
                            .orElse(false)
                            || path.contains("farmhouse")
                            || path.contains("farm_house")
                            || path.contains("cellar");
                })
                .orElse(false);
        if (explicitSafe) {
            return true;
        }

        int radius = 10;
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

    private static OvernightPenalty createOvernightPenalty(
            ServerPlayer player,
            boolean passOutSafe
    ) {
        if (passOutSafe) {
            return new OvernightPenalty(0, null);
        }
        boolean communityCenterComplete =
                com.stardew.craft.communitycenter.state.CCStoryFlags.hasFlag(
                        player,
                        com.stardew.craft.communitycenter.state.CCStoryFlags.CC_IS_COMPLETE);
        // Marriage/engagement is not yet represented by project player data.
        // Keep Harvey eligible instead of inventing a spouse from friendship.
        PassOutMailChoice choice = selectDefaultPassOutMail(
                ThreadLocalRandom.current(), communityCenterComplete, true);
        int baseLoss = calculateOvernightMoneyLoss(
                PlayerStardewDataAPI.getMoney(player), false);
        int adjustedLoss = choice == PassOutMailChoice.MARLON ? 0 : baseLoss;
        return new OvernightPenalty(adjustedLoss, choice.mailId());
    }

    static PassOutMailChoice selectDefaultPassOutMail(
            RandomGenerator random,
            boolean communityCenterComplete,
            boolean harveyEligible
    ) {
        RandomGenerator source = random == null ? ThreadLocalRandom.current() : random;
        if (communityCenterComplete && source.nextDouble() < 0.33D) {
            return PassOutMailChoice.MARLON;
        }
        List<PassOutMailChoice> candidates = new ArrayList<>();
        if (harveyEligible) {
            candidates.add(PassOutMailChoice.HARVEY);
        }
        if (!communityCenterComplete) {
            candidates.add(PassOutMailChoice.JOJA);
        }
        candidates.add(PassOutMailChoice.LINUS);
        return candidates.get(source.nextInt(candidates.size()));
    }

    enum PassOutMailChoice {
        MARLON("passedOut_Marlon"),
        HARVEY("passedOut_Harvey"),
        JOJA("passedOut_Joja"),
        LINUS("passedOut_Linus");

        private final String mailId;

        PassOutMailChoice(String mailId) {
            this.mailId = mailId;
        }

        String mailId() {
            return mailId;
        }
    }

    private record OvernightPenalty(int moneyLost, @javax.annotation.Nullable String mailId) {
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
                // C# Random.Next(min, max) excludes max.
                moneyToLose = lo + RANDOM.nextInt(hi - lo);
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

    private static List<ItemStack> removeDesertFestivalEggPenalty(ServerPlayer player) {
        if (!(player.level() instanceof net.minecraft.server.level.ServerLevel level)) {
            return List.of();
        }
        int eggCount = player.getInventory().countItem(com.stardew.craft.item.ModItems.CALICO_EGG.get());
        if (eggCount <= 0) {
            return List.of();
        }
        float percent = com.stardew.craft.festival.desert.DesertFestivalMineService.thinShellsActive(level) ? 0.5F : 0.2F;
        int removed = Math.max(0, (int)(eggCount * percent));
        int remaining = removed;
        for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(com.stardew.craft.item.ModItems.CALICO_EGG.get())) {
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
            }
        }
        int actualRemoved = removed - remaining;
        return actualRemoved <= 0
                ? List.of()
                : List.of(new ItemStack(com.stardew.craft.item.ModItems.CALICO_EGG.get(), actualRemoved));
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
    //  战斗救援事务
    // ──────────────────────────────────────

    /** Legacy entry point retained for old packet callers. */
    public static void teleportAfterPassOutAck(ServerPlayer player) {
        PassOutRecoveryData.Entry entry =
                PassOutRecoveryData.get(player.server).get(player.getUUID());
        if (entry != null && entry.combat()) {
            acknowledgeCombatCollapse(player, entry.transactionId());
        }
    }

    public static void acknowledgeCombatCollapse(ServerPlayer player, long transactionId) {
        PassOutRecoveryData recovery = PassOutRecoveryData.get(player.server);
        PassOutRecoveryData.Entry entry = recovery.get(player.getUUID());
        if (entry == null
                || !entry.combat()
                || entry.transactionId() != transactionId
                || entry.stage() != PassOutRecoveryData.Stage.COLLAPSING) {
            return;
        }
        Integer notBefore = combatCollapseNotBeforeTick.get(player.getUUID());
        if (notBefore != null
                && !hasReachedCombatCollapseDeadline(
                        player.server.getTickCount(), notBefore)) {
            LOGGER.warn(
                    "[PASSOUT] Ignored early combat-collapse ACK transaction={} for {}; {} ticks remain",
                    transactionId,
                    player.getName().getString(),
                    notBefore - player.server.getTickCount());
            return;
        }

        // Original Game1.updatePause restores ten health immediately before
        // the revive warp, while the player is still protected by killScreen.
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        data.setHealth(Math.min(10, data.getMaxHealth()));
        syncAndSave(player, data);
        combatCollapseNotBeforeTick.remove(player.getUUID());
        startCombatRescue(player, entry, recovery);
    }

    static boolean hasReachedCombatCollapseDeadline(int currentTick, int notBeforeTick) {
        return currentTick - notBeforeTick >= 0;
    }

    /** Resumes an interrupted collapse/rescue after reconnect or restart. */
    public static void resumePending(ServerPlayer player) {
        PassOutRecoveryData recovery = PassOutRecoveryData.get(player.server);
        PassOutRecoveryData.Entry entry = recovery.get(player.getUUID());
        if (entry == null || !entry.combat()) {
            return;
        }
        if (entry.stage() == PassOutRecoveryData.Stage.COLLAPSING) {
            PacketDistributor.sendToPlayer(player, new PassOutPayload(
                    entry.transactionId(),
                    entry.type(),
                    entry.moneyLost(),
                    entry.lostItems()
            ));
            scheduleCombatCollapseFallback(player, entry.transactionId());
            return;
        }
        if (entry.stage() == PassOutRecoveryData.Stage.OUTCOME_PENDING) {
            sendCombatOutcome(player, entry);
            return;
        }
        startCombatRescue(player, entry, recovery);
    }

    /**
     * The client ACK aligns the rescue with its eight-second presentation, but
     * cannot be allowed to own server progress. Lost packets, reconnect races,
     * or a broken client therefore fall through to the same idempotent
     * transition at the authoritative deadline.
     */
    private static void scheduleCombatCollapseFallback(ServerPlayer player, long transactionId) {
        java.util.UUID playerId = player.getUUID();
        var server = player.server;
        combatCollapseNotBeforeTick.put(
                playerId,
                server.getTickCount() + COMBAT_COLLAPSE_TICKS);
        com.stardew.craft.time.ServerRealTickTaskScheduler.schedule(
                server,
                COMBAT_COLLAPSE_TICKS,
                () -> {
                    ServerPlayer connected = server.getPlayerList().getPlayer(playerId);
                    if (connected != null) {
                        acknowledgeCombatCollapse(connected, transactionId);
                    }
                });
    }

    private static void startCombatRescue(
            ServerPlayer player,
            PassOutRecoveryData.Entry entry,
            PassOutRecoveryData recovery
    ) {
        if (CombatRescueCutsceneCoordinator.isPending(player.getUUID())) {
            return;
        }
        entry.setStage(PassOutRecoveryData.Stage.WAITING_FOR_DESTINATION);
        recovery.put(player.getUUID(), entry);

        CombatRescueCutsceneCoordinator.Completion completion =
                (rescuedPlayer, result) -> completeCombatRescue(rescuedPlayer, entry.transactionId(), result);
        boolean started = switch (entry.type()) {
            case COMBAT_MINE -> {
                CombatRescueCutsceneCoordinator.MineDialogue dialogue;
                try {
                    dialogue = CombatRescueCutsceneCoordinator.MineDialogue.valueOf(entry.dialogueName());
                } catch (RuntimeException ignored) {
                    dialogue = CombatRescueCutsceneCoordinator.MineDialogue.LINUS;
                }
                yield CombatRescueCutsceneCoordinator.beginMineRescue(
                        player, entry.rescuerNpcId(), dialogue, completion);
            }
            case COMBAT_DESERT_FESTIVAL ->
                    CombatRescueCutsceneCoordinator.beginDesertFestivalRecovery(player, completion);
            default -> CombatRescueCutsceneCoordinator.beginHospitalRescue(player, completion);
        };

        if (!started) {
            completeCombatRescue(
                    player,
                    entry.transactionId(),
                    CombatRescueCutsceneCoordinator.Result.DESTINATION_UNAVAILABLE
            );
        }
    }

    private static void completeCombatRescue(
            ServerPlayer player,
            long transactionId,
            CombatRescueCutsceneCoordinator.Result result
    ) {
        PassOutRecoveryData recovery = PassOutRecoveryData.get(player.server);
        PassOutRecoveryData.Entry entry = recovery.get(player.getUUID());
        if (entry == null || !entry.combat() || entry.transactionId() != transactionId) {
            return;
        }

        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        int moneyLost = entry.moneyLost();
        List<ItemStack> lostItems = entry.lostItems();
        if (entry.type() == PassOutType.COMBAT_MINE) {
            moneyLost = calcCombatMoneyLoss(data, player, true);
            lostItems = loseItemsOnDeath(player, data);
        } else if (entry.type() == PassOutType.COMBAT_OVERWORLD) {
            moneyLost = Math.min(1000, PlayerStardewDataAPI.getMoney(player));
            lostItems = loseItemsOnDeath(player, data);
        }

        if (moneyLost > 0) {
            PlayerStardewDataAPI.removeMoney(player, moneyLost);
        }
        data.setItemsLostLastDeath(lostItems);
        data.setEnergy(Math.min(data.getEnergy(), 2.0F));
        data.setHealth(Math.min(10, data.getMaxHealth()));
        data.setPassedOutFromCombat(false);
        syncAndSave(player, data);

        if (entry.type() == PassOutType.COMBAT_MINE
                || entry.type() == PassOutType.COMBAT_DESERT_FESTIVAL) {
            com.stardew.craft.mining.MiningPlayerData miningData =
                    com.stardew.craft.mining.MiningDataManager.getPlayerData(player);
            miningData.setCurrentFloor(0);
            com.stardew.craft.mining.MiningDataManager.savePlayerData(player, miningData);
        }

        entry.setOutcome(moneyLost, lostItems);
        entry.setStage(PassOutRecoveryData.Stage.OUTCOME_PENDING);
        recovery.put(player.getUUID(), entry);
        grantCombatDeathRecovery(player);
        sendCombatOutcome(player, entry);
        LOGGER.info("[PASSOUT] Completed combat rescue {} for {} with result {}; lost {}g and {} item stacks",
                transactionId, player.getName().getString(), result, moneyLost, lostItems.size());
    }

    private static void sendCombatOutcome(
            ServerPlayer player,
            PassOutRecoveryData.Entry entry
    ) {
        PacketDistributor.sendToPlayer(player, new CombatRescueOutcomePayload(
                entry.transactionId(),
                entry.type(),
                entry.moneyLost(),
                entry.lostItems()));
    }

    /** Removes the durable outcome only after the client confirms receipt. */
    public static void acknowledgeCombatOutcome(ServerPlayer player, long transactionId) {
        PassOutRecoveryData recovery = PassOutRecoveryData.get(player.server);
        PassOutRecoveryData.Entry entry = recovery.get(player.getUUID());
        if (entry == null
                || !entry.combat()
                || entry.transactionId() != transactionId
                || entry.stage() != PassOutRecoveryData.Stage.OUTCOME_PENDING) {
            return;
        }
        recovery.remove(player.getUUID());
    }

    /**
     * Overnight-only wake-up warp. This must not grant combat recovery,
     * reset mine progress through the combat path, or clear combat state.
     */
    public static void teleportToFarmSpawn(ServerPlayer player) {
        var server = player.server;
        var stardewLevel = server.getLevel(ModDimensions.STARDEW_VALLEY);
        if (stardewLevel == null) return;

        // 查询玩家的农场出生点
        com.stardew.craft.farm.FarmInstanceRegistry registry = com.stardew.craft.farm.FarmInstanceRegistry.get();
        com.stardew.craft.farm.FarmInstance farm = registry.getFarmForPlayer(player.getUUID());
        net.minecraft.core.BlockPos spawnPos = PlayerDataManager.getPlayerData(player)
                .getLastSleepPoint()
                .filter(pos -> farm != null
                        && farm.contains(pos)
                        && isSleepBlock(stardewLevel.getBlockState(pos)))
                .orElseGet(() -> registry.getFarmSpawnPoint(player.getUUID()));
        if (spawnPos == null) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("stardewcraft.warp.farm.unavailable"), true);
            LOGGER.warn("[PASS_OUT] Player {} has no farm spawn; skipping farm return teleport.",
                player.getName().getString());
            return;
        }

        double sx = spawnPos.getX() + 0.5;
        double sy = spawnPos.getY();
        double sz = spawnPos.getZ() + 0.5;

        ModTeleport.to(player, stardewLevel, sx, sy, sz, player.getYRot(), player.getXRot());
    }

    private static boolean isSleepBlock(net.minecraft.world.level.block.state.BlockState state) {
        return state.getBlock() instanceof BedBlock
                || state.is(ModBlocks.BED_1.get())
                || state.is(ModBlocks.BED_2.get());
    }

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
        EXHAUSTION_STAMINA(3),
        COMBAT_DESERT_FESTIVAL(4);

        private final int id;
        PassOutType(int id) { this.id = id; }
        public int getId() { return id; }

        public static PassOutType fromId(int id) {
            return switch (id) {
                case 0 -> COMBAT_MINE;
                case 1 -> COMBAT_OVERWORLD;
                case 2 -> EXHAUSTION_2AM;
                case 3 -> EXHAUSTION_STAMINA;
                case 4 -> COMBAT_DESERT_FESTIVAL;
                default -> EXHAUSTION_2AM;
            };
        }
    }
}
