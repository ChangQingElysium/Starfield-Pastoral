package com.stardew.craft.time;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.core.ModMiningDimensions;
import com.stardew.craft.network.overnight.OvernightSettlementPayload;
import com.stardew.craft.network.overnight.OvernightSettlementTracker;
import com.stardew.craft.player.PlayerStardewData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 星露谷时间管理系统
 * 
 * 星露谷使用独立的绝对 dayTime，不依赖主世界昼夜或睡眠跳时。
 * 
 * 时间规则：
 * - 星露谷一天 = 6:00 AM 到 2:00 AM (次日)
 * - 默认按 MC 原版昼夜速度推进（20分钟一天）
 * - MC dayTime 0 = 6:00 AM, dayTime 20000 = 2:00 AM
 * - 当到达2:00 AM时，跳到下一天（dayTime重置为0）
 */
public class StardewTimeManager extends SavedData {

    private static final Set<String> IMPLEMENTED_DATE_TRIGGERED_MAIL = Set.of(
        "spring_2_1",
        "spring_5_1",
        "spring_11_1",
        "spring_19_1",
        "spring_20_1",
        "spring_25_1",
        "spring_6_2",
        "spring_15_2",
        "spring_21_2",
        "summer_1",
        "summer_3_1",
        "summer_6_1",
        "summer_13_1",
        "summer_14_1",
        "summer_19_1",
        "summer_20_1",
        "summer_25_1",
        "summer_6_2",
        "summer_15_2",
        "summer_21_2",
        "fall_1",
        "fall_2_1",
        "fall_3_1",
        "fall_6_1",
        "fall_8_1",
        "fall_18_1",
        "fall_19_1",
        "fall_27_1",
        "fall_6_2",
        "fall_19_2",
        "winter_1",
        "winter_2_1",
        "winter_6_1",
        "winter_12_1",
        "winter_14_1",
        "winter_17_1",
        "winter_18",
        "winter_21_1",
        "winter_26_1",
        "winter_27_1",
        "winter_5_2",
        "winter_13_2",
        "winter_19_2",
        "spring_1_2",
        "summer_1_2",
        "winter_1_2"
    );
    
    private static final String DATA_NAME = "stardew_time_data";
    
    // 时间常量
    public static final int MORNING_START = 360;     // 早上6:00 (6 * 60 = 360)
    public static final int MIDNIGHT = 1440;         // 0:00 (24:00)
    public static final int PASS_OUT_TIME = 1560;    // 凌晨2:00 (26:00)
    public static final int MINUTES_PER_HOUR = 60;
    public static final int MINUTES_PER_DAY = 1440;
    
    // 时间状态
    private int currentTime = MORNING_START;  // 当前时间（分钟），从MC dayTime同步
    private int currentDay = 1;                // 当前日期
    private int currentSeason = 0;             // 当前季节 (0=春, 1=夏, 2=秋, 3=冬)
    private int currentYear = 1;               // 当前年份

    /**
     * 星露谷共享时钟的绝对 dayTime。农场与矿井都读取这一份权威时间，
     * 主世界睡觉、命令或 gamerule 均不会改变它。
     */
    private long independentDayTime = 0;

    /** 小数倍率剩余量，例如 0.5 倍每两个 server tick 才推进一个 dayTime tick。 */
    private double timeAdvanceRemainder = 0.0D;

    /**
     * Pause-aware absolute tick used by the shared Stardew farm/mine simulation.
     * Initialized from the overworld gameTime when upgrading an existing save, then advanced only
     * while the Stardew simulation is running.
     */
    private long simulationGameTime = -1L;
    
    // 事件标记（防止重复触发）
    private boolean event1800Triggered = false;  // 18:00 事件
    private boolean event2200Triggered = false;  // 22:00 事件
    private boolean event0000Triggered = false;  // 0:00 事件
    private boolean event0130Triggered = false;  // 1:30 事件

    /** 最近触发过 10 分钟 tick 的时间桶（=currentTime/10），-1 表示本日尚未触发过。 */
    private int lastTenMinuteBucket = -1;
    
    public StardewTimeManager() {
    }

    // ── 独立 dayTime API ──

    /**
     * 获取星露谷共享时钟的绝对 dayTime。
     */
    public long getVirtualDayTime() {
        return independentDayTime;
    }

    /**
     * 兼容旧调用。参数不再参与计算。
     * @deprecated 使用 {@link #getVirtualDayTime()}。
     */
    @Deprecated
    public long getVirtualDayTime(ServerLevel anyLevel) {
        return independentDayTime;
    }

    public long getIndependentDayTime() {
        return independentDayTime;
    }

    /**
     * 按配置倍率推进一次 server tick。小数部分会跨 tick 精确累计。
     *
     * @return 本次实际推进的 dayTime tick 数
     */
    public long advanceIndependentDayTime(double multiplier) {
        TimeAdvance advance = calculateTimeAdvance(timeAdvanceRemainder, multiplier);
        timeAdvanceRemainder = advance.remainder();
        if (advance.wholeTicks() > 0L) {
            independentDayTime += advance.wholeTicks();
        }
        setDirty();
        return advance.wholeTicks();
    }

    /**
     * 纯计算入口，供单元测试验证任意小数倍率不会因四舍五入失真。
     */
    static TimeAdvance calculateTimeAdvance(double previousRemainder, double multiplier) {
        double safeMultiplier = Math.max(0.0D, multiplier);
        double accumulated = Math.max(0.0D, previousRemainder) + safeMultiplier;
        long wholeTicks = (long) Math.floor(accumulated + 1.0E-9D);
        double remainder = accumulated - wholeTicks;
        if (remainder < 1.0E-9D) {
            remainder = 0.0D;
        }
        return new TimeAdvance(wholeTicks, remainder);
    }

    static record TimeAdvance(long wholeTicks, double remainder) {}

    /** 将星露谷共享时钟跳到目标绝对时间。 */
    public void setVirtualDayTime(long targetDayTime) {
        independentDayTime = Math.max(0L, targetDayTime);
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            StardewTimePauseService.onAuthoritativeVirtualDayTimeSet(server, independentDayTime);
        }
        setDirty();
    }

    /**
     * 兼容旧调用。参数仅用于取得 server 并重基准暂停状态。
     * @deprecated 使用 {@link #setVirtualDayTime(long)}。
     */
    @Deprecated
    public void setVirtualDayTime(ServerLevel anyLevel, long targetDayTime) {
        independentDayTime = Math.max(0L, targetDayTime);
        StardewTimePauseService.onAuthoritativeVirtualDayTimeSet(anyLevel.getServer(), independentDayTime);
        setDirty();
    }

    public long getSimulationGameTime() {
        return simulationGameTime;
    }

    public void initializeSimulationGameTime(long fallbackGameTime) {
        if (simulationGameTime < 0L) {
            simulationGameTime = Math.max(0L, fallbackGameTime);
            setDirty();
        }
    }

    public void advanceSimulationGameTime() {
        if (simulationGameTime < 0L) {
            throw new IllegalStateException("Stardew simulation gameTime was not initialized");
        }
        simulationGameTime++;
        setDirty();
    }
    
    /**
     * 从MC时间设置当前星露谷时间（由DimensionEventHandler调用）
     * @param stardewMinutes 星露谷分钟（360 = 6:00 AM）
     */
    public void setCurrentTimeFromMC(int stardewMinutes) {
        // 只有时间变化时才更新
        if (stardewMinutes != currentTime) {
            currentTime = stardewMinutes;
            
            // 检查关键时间点
            checkTimeEvents();
            
            setDirty();
        }
    }
    
    /**
     * 检查并触发时间事件
     */
    private void checkTimeEvents() {
        // 每 10 分钟 tick — SDV parity: GameLocation.performTenMinuteUpdate
        // 当前用于 ore-pan-point 生成（ccFishTank 奖励）。
        int currentBucket = currentTime / 10;
        if (currentBucket != lastTenMinuteBucket) {
            lastTenMinuteBucket = currentBucket;
            var server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                com.stardew.craft.communitycenter.reward.panning.OrePanPointManager
                    .performTenMinuteUpdate(server);
                com.stardew.craft.fishing.splash.FishSplashTicker
                    .performTenMinuteUpdate(server);
                com.stardew.craft.weather.LightningStrikeScheduler
                    .performTenMinuteUpdate(server);
                com.stardew.craft.festival.FestivalService.onTimeChanged(server);
                com.stardew.craft.auction.AuctionService.onTimeChanged(server);
            }
        }

        // 18:00 (1080分钟)
        if (currentTime >= 1080 && !event1800Triggered) {
            event1800Triggered = true;
            // 这里可以触发动物回家等事件
        }
        
        // 22:00 (1320分钟)
        if (currentTime >= 1320 && !event2200Triggered) {
            event2200Triggered = true;
        }
        
        // 0:00 (1440分钟)
        if (currentTime >= 1440 && !event0000Triggered) {
            event0000Triggered = true;
        }
        
        // 1:30 (1530分钟)
        if (currentTime >= 1530 && !event0130Triggered) {
            event0130Triggered = true;
        }
    }
    
    /**
     * 进入下一天
     */
    public void advanceDay() {
        advanceDayWithSleepTime(currentTime);
    }

    /**
     * 进入下一天（可指定结算时的入睡时间）
     */
    public void advanceDayWithSleepTime(int sleepMinute) {
        int timeWentToSleepMinutes = sleepMinute;
        boolean seasonChanged = false;
        List<PendingOvernightSettlement> pendingSettlements = new ArrayList<>();
        int previousDay = currentDay;
        int previousSeason = currentSeason;
        int previousYear = currentYear;
        var server = ServerLifecycleHooks.getCurrentServer();
        @SuppressWarnings("null")
        ServerLevel settlementLevel = server == null ? null : server.getLevel(ModDimensions.STARDEW_VALLEY);
        String previousWeather = settlementLevel == null
            ? "Sun"
            : com.stardew.craft.weather.WeatherManager.getCurrentWeather(settlementLevel);

        currentDay++;
        
        // 检查是否需要换季（每季28天）
        if (currentDay > 28) {
            currentDay = 1;
            currentSeason++;
            seasonChanged = true;
            
            // 检查是否需要换年
            if (currentSeason > 3) {
                currentSeason = 0;
                currentYear++;
            }
        }
        
        // 重置时间为早上6:00
        currentTime = MORNING_START;
        
        // 重置事件标记
        resetEventFlags();
        
        StardewCraft.LOGGER.info("New day: Year {} - {} Day {}", 
            currentYear, getSeasonName(), currentDay);
        
        // 触发每日作物生长
        if (server != null) {
            int absDay = (currentYear - 1) * (28 * 4) + currentSeason * 28 + currentDay;
            int previousAbsDay = (previousYear - 1) * (28 * 4) + previousSeason * 28 + previousDay;
            int totalDaysPlayed = absDay;

            // SDV Farmer.dayupdate -> resetFriendshipsForNewDay. This settles
            // every persisted farmer, including farmhands who are offline tonight.
            com.stardew.craft.npc.runtime.NpcFriendshipDailyService.onNewDay(
                    server.overworld(), previousAbsDay, absDay);
            // SDV Farmer.dayupdate: age active NPC dialogue events and create
            // their one-day/week/month/year memory variants.
            com.stardew.craft.npc.runtime.NpcDialogueEventData.get(server).onNewDay();
            com.stardew.craft.npc.runtime.NpcDialogueTopicService.onNewDay(
                    server, previousWeather, previousYear);
            
            // 需要在星露谷维度触发，而不是主世界
            @SuppressWarnings("null")
            ServerLevel stardewLevel = server.getLevel(ModDimensions.STARDEW_VALLEY);
            if (stardewLevel != null) {
                com.stardew.craft.festival.FestivalService.onNewDay(stardewLevel);
                if (seasonChanged) {
                    // 先恢复公共区域被砍的杂草，再刷新季节外观
                    com.stardew.craft.farm.PublicAreaBlockTracker.get().restoreAll(stardewLevel);
                    com.stardew.craft.block.nature.WildWeedsBlock.refreshLoadedWeedsForSeason(stardewLevel, currentSeason);
                    com.stardew.craft.manager.JunimoGreenhouseRuneManager.get(stardewLevel).removeExpiredRunes(stardewLevel, currentSeason);
                }

                // 对齐 Stardew 的日结算语义：先确定“今天”的天气，再结算昨夜生长。
                com.stardew.craft.weather.WeatherManager.applyWeatherForNewDay(stardewLevel, currentDay, getSeasonName(), totalDaysPlayed);
                com.stardew.craft.npc.runtime.NpcSpawnManager.resetScheduledNpcsForNewDay(stardewLevel);
                // 确保所有室内区块（含温室）在日结算期间已加载，
                // 否则 growDaily / waterDaily 会因 isLoaded(pos)==false 跳过温室作物。
                com.stardew.craft.interior.InteriorSubspaceManager.setInteriorChunksForced(stardewLevel, true, "daily_settlement");
                try {
                    com.stardew.craft.farm.FarmDailyProcessHelper.beginDailyProcess(stardewLevel);
                    runWorldDailyStep("crops", () -> com.stardew.craft.manager.CropGrowthManager.get(stardewLevel).growDaily(stardewLevel));
                    runWorldDailyStep("trees", () -> com.stardew.craft.manager.TreeGrowthManager.get(stardewLevel).growDaily(stardewLevel));
                    runWorldDailyStep("fruit_trees", () -> com.stardew.craft.manager.FruitTreeGrowthManager.get(stardewLevel).growDaily(stardewLevel));
                    runWorldDailyStep("wild_tree_seeds", () -> com.stardew.craft.manager.WildTreeSeedManager.get(stardewLevel).onNewDay(stardewLevel, absDay));
                    runWorldDailyStep("farm_debris", () -> com.stardew.craft.farm.FarmDebrisDailyService.onNewDay(stardewLevel));
                    runWorldDailyStep("sprinklers", () -> com.stardew.craft.manager.SprinklerManager.get(stardewLevel).waterDaily(stardewLevel));
                    runWorldDailyStep("pasture_grass", () -> com.stardew.craft.manager.PastureGrassGrowthManager.get(stardewLevel).growDaily(stardewLevel));
                    runWorldDailyStep("animals", () -> com.stardew.craft.manager.AnimalGrowthManager.get(stardewLevel).growDaily(stardewLevel, timeWentToSleepMinutes));
                    runWorldDailyStep("fish_ponds", () -> com.stardew.craft.fishpond.service.FishPondDailyUpdateService.onNewDay(stardewLevel));
                    runWorldDailyStep("forage", () -> com.stardew.craft.manager.ForageSpawnService.onNewDay(stardewLevel, currentSeason));
                    runWorldDailyStep("forest_farm_forage", () -> com.stardew.craft.manager.ForageSpawnService.onNewDayForestFarms(stardewLevel, currentSeason));
                    runWorldDailyStep("artifact_spots", () -> com.stardew.craft.manager.ArtifactSpotSpawnService.onNewDay(stardewLevel, currentSeason));
                    runWorldDailyStep("quarry", () -> com.stardew.craft.manager.QuarrySpawnService.onNewDay(stardewLevel, getCurrentYear()));
                    runWorldDailyStep("coal_forest", () -> com.stardew.craft.manager.CoalForestClumpSpawnService.onNewDay(stardewLevel));
                    runWorldDailyStep("secret_woods", () -> com.stardew.craft.manager.SecretWoodsAccessManager.ensureEntranceReady(stardewLevel));
                    runWorldDailyStep("farm_cave", () -> com.stardew.craft.manager.FarmCaveDailyService.onNewDay(stardewLevel));
                    runWorldDailyStep("addon_farm_tasks", () ->
                            com.stardew.craft.api.v1.internal.farm.StardewFarmDailyTaskRegistry.runActiveFarms(
                                    stardewLevel, absDay, currentSeason, currentDay));
                } catch (Exception e) {
                    StardewCraft.LOGGER.error(
                        "[DAILY] World settlement lifecycle failed; continuing player settlement",
                        e
                    );
                } finally {
                    try {
                        com.stardew.craft.farm.FarmDailyProcessHelper.endDailyProcess(stardewLevel);
                    } finally {
                        // 日结算完成后立即释放室内区块，避免 784 区块永久 force-loaded
                        com.stardew.craft.interior.InteriorSubspaceManager.setInteriorChunksForced(stardewLevel, false, "daily_settlement_done");
                    }
                }
                
                // 多人农场：更新所有在线玩家的 lastOnlineDay
                {
                    com.stardew.craft.farm.FarmInstanceRegistry farmReg =
                            com.stardew.craft.farm.FarmInstanceRegistry.get();
                    for (net.minecraft.server.level.ServerPlayer sp : server.getPlayerList().getPlayers()) {
                        com.stardew.craft.farm.FarmInstance fi = farmReg.getFarmForPlayer(sp.getUUID());
                        if (fi != null) {
                            fi.setLastOnlineDay(absDay);
                            fi.setLastOnlineSeason(currentSeason);
                        }
                    }
                    farmReg.setDirty();
                }

                // 预测明天的天气
                com.stardew.craft.weather.WeatherManager.updateWeatherForNewDay(
                    stardewLevel, currentDay, getSeasonName(), totalDaysPlayed
                );
            }

            // 次日恢复：生命回满；能量按 SV 原版 dayupdate 规则恢复（疲惫则减半）。
            // SDV parity: 结算前先将所有出货箱 buffer 里剩余的物品记录到出货追踪器
            com.stardew.craft.blockentity.ShippingBinBlockEntity.flushAllForOvernight();
            com.stardew.craft.farm.FarmInstanceRegistry overnightFarmRegistry =
                com.stardew.craft.farm.FarmInstanceRegistry.get();
            for (var player : server.getPlayerList().getPlayers()) {
                if (player.level().dimension() != ModDimensions.STARDEW_VALLEY
                    && player.level().dimension() != ModMiningDimensions.STARDEW_MINING) {
                    continue;
                }
                // 新玩家（尚未创建/加入农场）豁免一切夜间结算：不扣体力、不发结算画面、不投递邮件流程
                if (!overnightFarmRegistry.hasFarm(player.getUUID())) {
                    com.stardew.craft.network.overnight.OvernightSettlementTracker.consumePayload(player);
                    com.stardew.craft.player.PassOutService.consumePassOutResult(player.getUUID());
                    continue;
                }

                if (player.isCreative()) {
                    com.stardew.craft.player.PlayerStardewDataAPI.cureExhaustion(player);
                    com.stardew.craft.player.PlayerStardewDataAPI.restoreEnergy(player, com.stardew.craft.player.PlayerStardewDataAPI.getMaxEnergy(player));
                    com.stardew.craft.mastery.MasteryBuffLifecycle.clearAllDailyMasteryBuffs(player);
                } else {
                    com.stardew.craft.player.PlayerStardewDataAPI.sleep(player, timeWentToSleepMinutes);
                    // 战斗死亡次日体力压到2
                    com.stardew.craft.player.PassOutService.applyCombatDeathEnergyPenalty(player);
                }
                com.stardew.craft.player.PlayerStardewDataAPI.setHealth(player, com.stardew.craft.player.PlayerStardewDataAPI.getMaxHealth(player));

                // SDV parity: Farmer.dayupdate → daysLeftForToolUpgrade--
                com.stardew.craft.shop.BlacksmithService.onNewDay(player);
                com.stardew.craft.shop.BlacksmithService.showToolUpgradeNotification(player);

                OvernightSettlementPayload settlementPayload = OvernightSettlementTracker.consumePayload(player);
                com.stardew.craft.player.PlayerStardewDataAPI.recordOvernightShippedItems(player, settlementPayload.shippedItems());
                List<PlayerStardewData.SkillLevelUp> appliedLevelUps = com.stardew.craft.player.PlayerStardewDataAPI.applyPendingSkillLevelUps(player);
                com.stardew.craft.player.PlayerStardewDataAPI.applySkillLevelRecipeUnlocks(player, appliedLevelUps);

                // SDV parity: getLevelPerk — 升级后回满体力和生命值
                if (!appliedLevelUps.isEmpty()) {
                    com.stardew.craft.player.PlayerStardewDataAPI.restoreEnergy(player, com.stardew.craft.player.PlayerStardewDataAPI.getMaxEnergy(player));
                    com.stardew.craft.player.PlayerStardewDataAPI.setHealth(player, com.stardew.craft.player.PlayerStardewDataAPI.getMaxHealth(player));
                }

                // 同步到客户端（HUD 依赖客户端缓存）
                com.stardew.craft.player.PlayerDataEventHandler.syncPlayerData(player, com.stardew.craft.player.PlayerDataManager.getPlayerData(player));

                // Quest: day started
                com.stardew.craft.quest.StardewQuestEvents.fireDayStarted(player, absDay);

                // 精通系统：5×Lv10 首次达成 → 早上推送 MasteryHint
                com.stardew.craft.mastery.MasteryOnboardingService.checkOnMorning(player);

                List<OvernightSettlementPayload.LevelUpData> overnightLevelUps = new ArrayList<>(settlementPayload.levelUps());
                for (PlayerStardewData.SkillLevelUp levelUp : appliedLevelUps) {
                    overnightLevelUps.add(new OvernightSettlementPayload.LevelUpData(levelUp.skill().getId(), levelUp.newLevel()));
                }

                // 消费该玩家的 2AM 晕倒结果（如果有的话）合并进结算包
                com.stardew.craft.player.PassOutService.PassOutResult passOutResult =
                    com.stardew.craft.player.PassOutService.consumePassOutResult(player.getUUID(), absDay);
                int passOutType = passOutResult != null ? passOutResult.type().getId() : -1;
                int passOutMoneyLost = passOutResult != null ? passOutResult.moneyLost() : 0;
                java.util.List<net.minecraft.world.item.ItemStack> passOutLostItems =
                    passOutResult != null ? passOutResult.lostItems() : java.util.List.of();

                OvernightSettlementPayload finalPayload = new OvernightSettlementPayload(
                    settlementPayload.shippedItems(),
                    List.copyOf(overnightLevelUps),
                    passOutType,
                    passOutMoneyLost,
                    passOutLostItems,
                    new OvernightSettlementPayload.OvernightContext(
                        previousDay,
                        previousSeason,
                        previousYear,
                        currentDay,
                        currentSeason,
                        currentYear,
                        previousWeather
                    )
                );

                // Persist before the network send. If the player disconnects
                // during save/fade/menu playback, login can replay the exact
                // same settlement instead of silently losing level-up,
                // shipping or pass-out presentation.
                OvernightSettlementTracker.storePendingSettlement(player, finalPayload);
                // 等全部世界/邮件日结算和存档完成后，再统一打开夜间菜单。
                pendingSettlements.add(new PendingOvernightSettlement(player, finalPayload));
            }

            if (stardewLevel != null) {
                java.util.List<net.minecraft.server.level.ServerPlayer> stardewPlayers = server.getPlayerList().getPlayers().stream()
                    .filter(player -> player.level().dimension() == ModDimensions.STARDEW_VALLEY)
                    .toList();
                com.stardew.craft.specialorder.SpecialOrderManager.onNewDay(stardewLevel, stardewPlayers);
                com.stardew.craft.lostandfound.LostAndFoundService.onNewDay(stardewLevel);
                com.stardew.craft.book.BooksellerSchedule.onNewDay(stardewLevel, stardewPlayers);
                com.stardew.craft.shop.BooksellerEvents.forceCheckNow(stardewLevel);
            }
        }

        // Reset per-player shop stock for the new day (SDV: SynchronizedShopStock parity)
        com.stardew.craft.shop.ShopStockTracker.resetForNewDay();

        // 邮件系统：将 mailForTomorrow 队列投递到 mailbox
        if (server != null) {
            com.stardew.craft.mail.MailService.deliverAllTomorrowMail(server);

            // SDV _newDayAfterFade: first pick at most one random friendship
            // gift letter, then process the remaining morning mail sources.
            for (net.minecraft.server.level.ServerPlayer sp : server.getPlayerList().getPlayers()) {
                com.stardew.craft.npc.runtime.NpcFriendshipMailService.onNewDay(sp);
                scheduleMailByDate(sp, currentSeason, currentDay);
                com.stardew.craft.npc.runtime.NpcFriendshipRecipeMailService.onNewDay(sp);
            }
        }

        setDirty();

        if (server != null) {
            // PlayerStardewData 的字段会在上面的日结算中批量变化；确保 SavedData
            // 与日期、邮件及世界状态一起写盘，再让客户端显示原版 SaveGameMenu 节奏。
            com.stardew.craft.player.PlayerDataManager.get().setDirty();
            server.saveEverything(true, false, false);

            for (PendingOvernightSettlement pending : pendingSettlements) {
                net.minecraft.server.level.ServerPlayer connectedPlayer =
                    server.getPlayerList().getPlayer(pending.player().getUUID());
                if (connectedPlayer == null) {
                    continue;
                }

                // 扫描并排队所有 wake_up 剧情，等客户端关闭结算画面后按序播放。
                com.stardew.craft.cutscene.server.WakeUpEventScheduler.enqueueAtNightSettlement(connectedPlayer);
                com.stardew.craft.time.StardewTimePauseService.beginOvernightSettlement(connectedPlayer);
                // 原版即使没有出货或升级，也会显示 SaveGameMenu。
                PacketDistributor.sendToPlayer(connectedPlayer, pending.payload());
            }
        }
    }

    private record PendingOvernightSettlement(
        net.minecraft.server.level.ServerPlayer player,
        OvernightSettlementPayload payload
    ) {
    }

    private static void runWorldDailyStep(String name, Runnable step) {
        try {
            step.run();
        } catch (Exception exception) {
            StardewCraft.LOGGER.error("[DAILY] Step '{}' failed; continuing remaining settlement steps", name, exception);
        }
    }

    /**
     * SDV 日期触发邮件 — 根据当前季节/日期安排邮件。
     * 模拟 SDV Game1._newDayAfterFade：优先匹配 season_day_year，再匹配 season_day。
     * 同时处理里程碑邮件（父母信等按天数触发的邮件）。
     */
    private void scheduleMailByDate(net.minecraft.server.level.ServerPlayer player, int season, int day) {
        int globalDays = (currentYear - 1) * (28 * 4) + currentSeason * 28 + currentDay;
        schedulePersonalMailForAbsoluteDay(player, globalDays);
        scheduleGlobalCalendarMail(player, season, day);
    }

    private void schedulePersonalMailForAbsoluteDay(net.minecraft.server.level.ServerPlayer player, int globalDays) {
        // 初始化玩家的首次加入天数（用于里程碑/父母信件的相对天数计算）
        com.stardew.craft.player.PlayerStardewData pData =
                com.stardew.craft.player.PlayerDataManager.getPlayerData(player);
        if (pData.getFirstJoinDay() < 0) {
            pData.setFirstJoinDay(globalDays);
        }

        // 玩家个人年份（用于年份限定邮件，如 spring_2_1 = 春2日个人第1年）
        int personalDays = Math.max(0, globalDays - pData.getFirstJoinDay());
        int personalYear = personalDays / (28 * 4) + 1;

        // 个人日历：从加入当天起算的"季节/日"。这样 _<year> 邮件总是按
        // "玩家进入服务器后的第 N 个游戏日"触发，避免玩家如果在春5日才进服
        // 就永远收不到 spring_2_1 这类信。
        int personalSeason = (personalDays / 28) % 4;
        int personalDay = (personalDays % 28) + 1;
        String personalSeasonName = switch (personalSeason) {
            case 0 -> "spring";
            case 1 -> "summer";
            case 2 -> "fall";
            case 3 -> "winter";
            default -> "";
        };

        // SDV parity: season_day_year 用个人日历触发（spring_2_1 = 进服后第 2 个游戏日早晨）
        String keyWithYear = personalSeasonName + "_" + personalDay + "_" + personalYear;
        if (shouldDeliverDateTriggeredMail(keyWithYear)) {
            com.stardew.craft.mail.MailService.addMail(player, keyWithYear);
        }

        // 1.6 TriggerActions: parent letters are lifetime-earnings and
        // player-gender gated, not day milestones.
        scheduleParentMail(player);
    }

    private void scheduleGlobalCalendarMail(net.minecraft.server.level.ServerPlayer player, int season, int day) {
        String seasonName = switch (season) {
            case 0 -> "spring";
            case 1 -> "summer";
            case 2 -> "fall";
            case 3 -> "winter";
            default -> "";
        };

        // season_day 只投递当前已落地的非节日日期信；节日通知信由 FestivalRegistry 元数据统一控制。
        String keyNoYear = seasonName + "_" + day;
        if (shouldDeliverDateTriggeredMail(keyNoYear)) {
            if ("winter_18".equals(keyNoYear)) {
                com.stardew.craft.mail.MailService.addRecurringMail(player, keyNoYear);
            } else {
                com.stardew.craft.mail.MailService.addMail(player, keyNoYear);
            }
        }
        for (String festivalMailId : com.stardew.craft.festival.FestivalService.activeFestivalAnnouncementMailIdsForDate(season, day)) {
            if (com.stardew.craft.mail.MailRegistry.contains(festivalMailId)) {
                if ("winter_24".equals(festivalMailId)) {
                    com.stardew.craft.mail.MailService.addRecurringMail(player, festivalMailId);
                } else {
                    com.stardew.craft.mail.MailService.addMail(player, festivalMailId);
                }
            }
        }
    }

    private boolean shouldDeliverDateTriggeredMail(String mailId) {
        return IMPLEMENTED_DATE_TRIGGERED_MAIL.contains(mailId)
            && com.stardew.craft.mail.MailRegistry.contains(mailId);
    }

    /**
     * 玩家登录时补跑一次当日邮件调度。
     *
     * 用途：
     * - 补发玩家离线跨日后仍应在“今天”可见的个人日期邮件
     * - 确保依赖日期邮件的个人剧情前置不会因为玩家不是在 6:00 过夜在线而缺失
     *
     * MailService.addMail 内部会做去重，因此重复调用是安全的。
     */
    public void syncDateTriggeredMailOnLogin(net.minecraft.server.level.ServerPlayer player) {
        int currentAbsoluteDay = (currentYear - 1) * (28 * 4) + currentSeason * 28 + currentDay;
        com.stardew.craft.player.PlayerStardewData pData =
                com.stardew.craft.player.PlayerDataManager.getPlayerData(player);
        int firstJoinDay = pData.getFirstJoinDay();
        if (firstJoinDay < 0) {
            pData.setFirstJoinDay(currentAbsoluteDay);
            firstJoinDay = currentAbsoluteDay;
        }

        // 离线多天后登录时，补跑所有错过的个人日期邮件，
        // 避免 spring_2_1 这类一次性个人前置被永久漏掉。
        for (int absoluteDay = firstJoinDay; absoluteDay <= currentAbsoluteDay; absoluteDay++) {
            schedulePersonalMailForAbsoluteDay(player, absoluteDay);
        }

        // 全局日历邮件（节日通知等）只补今天，不回放过去的服务器公共广播。
        scheduleGlobalCalendarMail(player, currentSeason, currentDay);
    }

    /** Exact Data/TriggerActions parent-mail thresholds and gender branch. */
    private void scheduleParentMail(
            net.minecraft.server.level.ServerPlayer player
    ) {
        com.stardew.craft.player.PlayerStardewData data =
                com.stardew.craft.player.PlayerDataManager
                        .getPlayerData(player);
        long earnings = data.getTotalMoneyEarned();
        String prefix = data.isMale() ? "mom" : "dad";
        int[] thresholds = {5_000, 15_000, 32_000, 120_000};
        for (int index = 0; index < thresholds.length; index++) {
            if (earnings >= thresholds[index]) {
                com.stardew.craft.mail.MailService.addMail(
                        player, prefix + (index + 1));
            }
        }
    }

    /**
     * 重置事件标记
     */
    private void resetEventFlags() {
        event1800Triggered = false;
        event2200Triggered = false;
        event0000Triggered = false;
        event0130Triggered = false;
        lastTenMinuteBucket = -1;
    }
    
    /**
     * 获取当前小时（0-25）
     */
    public int getHour() {
        return currentTime / MINUTES_PER_HOUR;
    }
    
    /**
     * 获取当前分钟（0-59）
     */
    public int getMinute() {
        return currentTime % MINUTES_PER_HOUR;
    }

    /**
     * 获取格式化的时间字符串（例如："14:30"）
     */
    public String getFormattedTime() {
        int hour = getHour();
        int minute = getMinute();
        
        // 将24+小时转换回0-23
        if (hour >= 24) {
            hour -= 24;
        }
        
        return String.format("%02d:%02d", hour, minute);
    }
    
    /**
     * 获取12小时制时间字符串（例如："2:30 PM"）
     */
    public String getFormattedTime12Hour() {
        int hour = getHour();
        int minute = getMinute();
        
        if (hour >= 24) {
            hour -= 24;
        }
        
        String period = hour >= 12 ? "PM" : "AM";
        int displayHour = hour % 12;
        if (displayHour == 0) displayHour = 12;
        
        return String.format("%d:%02d %s", displayHour, minute, period);
    }
    
    /**
     * 获取季节名称
     */
    public String getSeasonName() {
        return switch (currentSeason) {
            case 0 -> "Spring";
            case 1 -> "Summer";
            case 2 -> "Fall";
            case 3 -> "Winter";
            default -> "Unknown";
        };
    }
    
    // Getters
    public int getCurrentTime() { return currentTime; }
    public int getCurrentDay() { return currentDay; }
    public int getCurrentSeason() { return currentSeason; }
    public int getCurrentYear() { return currentYear; }

    /** 绝对游戏日（year/season/day 合成，用于跨年单调递增的天数键）。 */
    public int getAbsoluteDay() {
        return (currentYear - 1) * (28 * 4) + currentSeason * 28 + currentDay;
    }
    
    // Setters (用于调试或特殊情况)
    public void setCurrentTime(int time) { 
        this.currentTime = time; 
        setDirty(); 
    }
    
    public void setCurrentDay(int day) { 
        this.currentDay = day; 
        setDirty(); 
    }
    
    public void setCurrentSeason(int season) { 
        this.currentSeason = season; 
        setDirty(); 
    }
    
    public void setCurrentYear(int year) { 
        this.currentYear = year; 
        setDirty(); 
    }
    
    // SavedData 实现
    @Override
    public @javax.annotation.Nonnull CompoundTag save(@javax.annotation.Nonnull CompoundTag tag, @javax.annotation.Nonnull net.minecraft.core.HolderLookup.Provider provider) {
        tag.putInt("currentTime", currentTime);
        tag.putInt("currentDay", currentDay);
        tag.putInt("currentSeason", currentSeason);
        tag.putInt("currentYear", currentYear);
        tag.putLong("independentDayTime", independentDayTime);
        tag.putDouble("timeAdvanceRemainder", timeAdvanceRemainder);
        tag.putLong("simulationGameTime", simulationGameTime);
        
        tag.putBoolean("event1800", event1800Triggered);
        tag.putBoolean("event2200", event2200Triggered);
        tag.putBoolean("event0000", event0000Triggered);
        tag.putBoolean("event0130", event0130Triggered);
        
        return tag;
    }
    
    public static StardewTimeManager load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
        StardewTimeManager data = new StardewTimeManager();
        
        // 使用默认值，避免新存档时间从0开始
        data.currentTime = tag.contains("currentTime") ? tag.getInt("currentTime") : MORNING_START;
        data.currentDay = tag.contains("currentDay") ? tag.getInt("currentDay") : 1;
        data.currentSeason = tag.contains("currentSeason") ? tag.getInt("currentSeason") : 0;
        data.currentYear = tag.contains("currentYear") ? tag.getInt("currentYear") : 1;
        
        if (tag.contains("independentDayTime")) {
            data.independentDayTime = Math.max(0L, tag.getLong("independentDayTime"));
            data.timeAdvanceRemainder = tag.contains("timeAdvanceRemainder")
                ? Math.max(0.0D, Math.min(0.999999999D, tag.getDouble("timeAdvanceRemainder")))
                : 0.0D;
        } else {
            // 旧字段 dayTimeOffset 只有相对主世界的偏移，离线加载时没有可靠基准。
            // 日期与 currentTime 才是旧存档持久化的权威状态，据此恢复绝对时间。
            long completedDays = (data.currentYear - 1L) * 112L
                + data.currentSeason * 28L
                + (data.currentDay - 1L);
            long timeOfDay = com.stardew.craft.event.DimensionEventHandler
                .stardewMinutesToMcTime(data.currentTime);
            data.independentDayTime = Math.max(0L, completedDays * 24000L + timeOfDay);
            data.timeAdvanceRemainder = 0.0D;
            StardewCraft.LOGGER.info(
                "[STARDEW TIME] Migrated legacy offset clock to independentDayTime={}",
                data.independentDayTime
            );
        }
        data.simulationGameTime = tag.contains("simulationGameTime")
            ? tag.getLong("simulationGameTime")
            : -1L;
        data.event1800Triggered = tag.getBoolean("event1800");
        data.event2200Triggered = tag.getBoolean("event2200");
        data.event0000Triggered = tag.getBoolean("event0000");
        data.event0130Triggered = tag.getBoolean("event0130");
        
        StardewCraft.LOGGER.info("[STARDEW TIME] Loaded SavedData: time={}, day={}, season={}, year={}", 
            data.currentTime, data.currentDay, data.currentSeason, data.currentYear);
        
        return data;
    }
    
    /**
     * 获取或创建时间管理器实例
     */
    @SuppressWarnings("null")
    public static StardewTimeManager get() {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return new StardewTimeManager(); // 客户端返回临时实例
        }
        
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
            new SavedData.Factory<StardewTimeManager>(
                StardewTimeManager::new,
                StardewTimeManager::load,
                null  // DataFixTypes
            ),
            DATA_NAME
        );
    }
}
