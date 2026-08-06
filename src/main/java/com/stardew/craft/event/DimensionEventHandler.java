package com.stardew.craft.event;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.core.ModMiningDimensions;
import com.stardew.craft.dimension.StardewValleyMapBootstrap;
import com.stardew.craft.dimension.StardewValleyPrebuiltRegionInstaller;
import com.stardew.craft.interior.InteriorSubspaceManager;
import com.stardew.craft.network.TimeSyncPacket;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.level.block.BedBlock;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import net.neoforged.neoforge.event.level.SleepFinishedTimeEvent;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;

/**
 * 维度事件处理器 - 使用MC原版昼夜循环
 * 
 * 新时间系统设计：
 * - 开启原版doDaylightCycle，让MC自然推进dayTime
 * - 从MC dayTime计算星露谷时间（分钟）
 * - dayTime >= 20000 (2:00 AM) 时强制晕倒并跳到下一天
 * 
 * MC dayTime 映射：
 * - dayTime 0 = 6:00 AM（日出）
 * - dayTime 6000 = 12:00 PM（正午）
 * - dayTime 12000 = 6:00 PM（日落）
 * - dayTime 18000 = 12:00 AM（午夜）
 * - dayTime 20000 = 2:00 AM（强制晕倒，一天结束）
 * 
 * 转换公式：星露谷分钟 = (dayTime / 1000.0 + 6) * 60
 */
@EventBusSubscriber(modid = StardewCraft.MODID)
public class DimensionEventHandler {

    // ── 深夜时间点常量（MC dayTime）──
    // 对标 SDV: case 2400 (midnight), case 2500 (1AM), case 2600 (2AM), case 2800 (2:40AM)
    private static final long MIDNIGHT_TIME = 18000;       // 0:00 AM
    private static final long ONE_AM_TIME = 19000;         // 1:00 AM
    private static final long DAY_END_TIME = 20000;        // 2:00 AM — 强制晕倒
    @SuppressWarnings("unused")
    private static final long DAY_HARD_END_TIME = 22000;   // 2:40 AM — 强制关菜单安全网
    
    // 防止重复触发新的一天
    private static boolean dayAdvancing = false;
    /** True while a pass-out animation is finishing before the authoritative morning transition. */
    private static boolean passOutAdvanceScheduled = false;
    // 深夜警告标记（每天重置一次）
    private static boolean midnightWarned = false;   // 0:00 警告
    private static boolean oneAMWarned = false;      // 1:00 警告
    private static boolean twoAMWarned = false;      // 2:00 警告
    private static int lastAnimalTenMinuteDayKey = Integer.MIN_VALUE;
    private static int lastAnimalTenMinuteSlot = Integer.MIN_VALUE;

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        dayAdvancing = false;
        passOutAdvanceScheduled = false;
        midnightWarned = false;
        oneAMWarned = false;
        twoAMWarned = false;
        SleepVoteTracker.clearVotes();
        com.stardew.craft.player.PassOutService.clearRuntimeState();
    }

    @SuppressWarnings("null")
    /**
     * Public entry point for triggering day advance from outside this class
     * (e.g. from logout handler when sleep votes are satisfied).
     */
    public static void triggerAdvance(ServerLevel stardewLevel, int sleepMinute, String reason) {
        advanceToNextMorning(stardewLevel, sleepMinute, reason);
    }

    @SuppressWarnings("null")
    private static void advanceToNextMorning(ServerLevel sourceLevel, int sleepMinute, String reason) {
        if (dayAdvancing) {
            return;
        }
        dayAdvancing = true;
        try {
            var server = sourceLevel.getServer();
            StardewTimeManager timeManager = StardewTimeManager.get();

            // === 通过 offset 推进虚拟 dayTime，不修改主世界 ===
            long currentVirtual = timeManager.getVirtualDayTime();
            long currentDay = currentVirtual / 24000;
            long newDayTime = (currentDay + 1) * 24000;
            timeManager.setVirtualDayTime(newDayTime);

            // === 只发时间包给星露谷维度玩家 ===
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                ServerLevel playerLevel = player.serverLevel();
                if (isStardewDimension(playerLevel)) {
                    player.connection.send(new ClientboundSetTimePacket(
                        playerLevel.getGameTime(),
                        newDayTime,
                        true // 星露谷始终有昼夜循环
                    ));
                }
            }

            // 日结算逻辑（作物生长、天气、玩家恢复等）
            try {
                timeManager.advanceDayWithSleepTime(sleepMinute);
            } catch (Exception e) {
                StardewCraft.LOGGER.error("Error during advanceDayWithSleepTime (day still advanced to prevent freeze)", e);
                // A collapse start is a client input lock. If settlement
                // construction fails, explicitly terminate that presentation
                // instead of leaving affected clients black forever.
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    if (com.stardew.craft.player.PassOutService
                            .hasPendingPassOutResult(player.getUUID())) {
                        PacketDistributor.sendToPlayer(
                                player,
                                new com.stardew.craft.network.overnight
                                        .OvernightCollapseCancelPayload());
                    }
                }
            }
            wakeSleepingStardewPlayers(server);

            PacketDistributor.sendToAllPlayers(TimeSyncPacket.fromTimeManager(timeManager));
            lastAnimalTenMinuteDayKey = Integer.MIN_VALUE;
            lastAnimalTenMinuteSlot = Integer.MIN_VALUE;

            // 重置深夜警告标记（新的一天）
            midnightWarned = false;
            oneAMWarned = false;
            twoAMWarned = false;
            StardewCraft.LOGGER.info("Stardew day advanced to next morning by {} (sleepMinute={})", reason, sleepMinute);
        } finally {
            dayAdvancing = false;
        }
    }

    @SuppressWarnings("null")
    public static boolean requestSleepAdvance(ServerPlayer player, int sleepMinute) {
        BlockPos bedPos = SleepInteractionHandler.consumePendingBedPos(player);
        return requestSleepAdvance(player, sleepMinute, bedPos);
    }

    @SuppressWarnings("null")
    public static void requestPassOutAdvance(ServerPlayer player) {
        if (player == null) {
            return;
        }
        if (player.level().dimension() != ModDimensions.STARDEW_VALLEY
            && player.level().dimension() != ModMiningDimensions.STARDEW_MINING) {
            return;
        }
        ServerLevel stardewLevel = player.server.getLevel(ModDimensions.STARDEW_VALLEY);
        if (stardewLevel == null) {
            return;
        }

        if (player.getVehicle() != null) {
            player.stopRiding();
        }
        if (player.isUsingItem()) {
            player.stopUsingItem();
        }

        int sleepMinute = StardewTimeManager.get().getCurrentTime();
        if (shouldReturnEarlyPassOutToBed(
                sleepMinute,
                SleepVoteTracker.countStardewPlayersOnline(player.server))) {
            // Original multiplayer: stamina collapse before 2AM returns only
            // this farmer to bed. It contributes readiness, but the collapse
            // itself never calls PassOutNewDay.
            boolean readyToAdvance = SleepVoteTracker.registerEarlyPassOut(player, sleepMinute);
            java.util.UUID collapsedPlayerId = player.getUUID();
            int settlementDay = StardewTimeManager.get().getAbsoluteDay() + 1;
            if (readyToAdvance) {
                schedulePassOutAdvance(
                        stardewLevel,
                        sleepMinute,
                        "pass_out_stamina_all_ready",
                        SleepVoteTracker.getVotedPlayerSnapshot());
                return;
            }
            com.stardew.craft.time.ServerRealTickTaskScheduler.schedule(
                    player.server,
                    com.stardew.craft.network.overnight.OvernightCollapseTimeline.COLLAPSE_TICKS,
                    () -> {
                        ServerPlayer collapsedPlayer =
                                player.server.getPlayerList().getPlayer(collapsedPlayerId);
                        // If another farmer advanced the day while animation
                        // 293 was running, settlement already consumed this
                        // entry and owns the morning warp/presentation.
                        if (collapsedPlayer == null
                                || !com.stardew.craft.player.PassOutService
                                        .hasPendingPassOutResult(collapsedPlayerId)) {
                            return;
                        }
                        com.stardew.craft.player.PassOutService
                                .teleportToFarmSpawn(collapsedPlayer);
                        PacketDistributor.sendToPlayer(
                                collapsedPlayer,
                                new com.stardew.craft.network.overnight
                                        .OvernightCollapseReturnToBedPayload(settlementDay));
                    });
            return;
        }
        if (SleepVoteTracker.castPassOutVote(player, sleepMinute)) {
            schedulePassOutAdvance(
                    stardewLevel,
                    SleepVoteTracker.getLatestSleepMinute(),
                    "pass_out_stamina",
                    SleepVoteTracker.getVotedPlayerSnapshot());
        }
    }

    @SuppressWarnings("null")
    public static boolean requestSleepAdvance(
            ServerPlayer player,
            int sleepMinute,
            BlockPos bedPos
    ) {
        if (player == null) {
            return false;
        }
        if (player.level().dimension() != ModDimensions.STARDEW_VALLEY
            && player.level().dimension() != ModMiningDimensions.STARDEW_MINING) {
            return false;
        }
        ServerLevel stardewLevel = player.server.getLevel(ModDimensions.STARDEW_VALLEY);
        if (stardewLevel == null) {
            return false;
        }

        if (bedPos == null || !isSleepAnchor(player, bedPos)) {
            return false;
        }
        if (!player.isSleeping()) {
            return false;
        }

        // 多人投票：只有所有 Stardew 维度玩家都投票后才推进
        if (SleepVoteTracker.castVote(player, sleepMinute)) {
            int effectiveSleepMinute = SleepVoteTracker.getLatestSleepMinute();
            java.util.Set<java.util.UUID> votedPlayers = SleepVoteTracker.getVotedPlayerSnapshot();
            java.util.Set<java.util.UUID> pendingPassOutPlayers = collectPendingPassOutPlayers(player.server, votedPlayers);
            int remainingCollapseTicks = SleepVoteTracker.remainingPassOutAnimationTicks(
                    player.server,
                    pendingPassOutPlayers,
                    com.stardew.craft.network.overnight.OvernightCollapseTimeline.COLLAPSE_TICKS);
            if (remainingCollapseTicks > 0) {
                schedulePassOutAdvance(
                        stardewLevel,
                        effectiveSleepMinute,
                        "sleep_confirm_after_pass_out",
                        votedPlayers);
                return true;
            }
            SleepVoteTracker.clearVotes();
            advanceToNextMorning(stardewLevel, effectiveSleepMinute, "sleep_confirm");
            teleportPlayersToFarmSpawn(player.server, pendingPassOutPlayers);
        }
        return true;
    }

    static boolean shouldReturnEarlyPassOutToBed(
            int stardewMinute,
            int onlineStardewPlayers
    ) {
        return stardewMinute < 1560 && onlineStardewPlayers > 1;
    }

    private static void schedulePassOutAdvance(
            ServerLevel stardewLevel,
            int sleepMinute,
            String reason,
            java.util.Set<java.util.UUID> votedPlayers
    ) {
        if (passOutAdvanceScheduled) {
            return;
        }
        var server = stardewLevel.getServer();
        java.util.Set<java.util.UUID> voteSnapshot = java.util.Set.copyOf(votedPlayers);
        java.util.Set<java.util.UUID> pendingPassOutPlayers =
                collectPendingPassOutPlayers(server, voteSnapshot);
        int delay = SleepVoteTracker.remainingPassOutAnimationTicks(
                server,
                pendingPassOutPlayers,
                com.stardew.craft.network.overnight.OvernightCollapseTimeline.COLLAPSE_TICKS);
        if (!pendingPassOutPlayers.isEmpty() && delay == 0) {
            // 2AM/single-player collapses are entered directly rather than
            // through registerEarlyPassOut, so their animation starts now.
            delay = com.stardew.craft.network.overnight
                    .OvernightCollapseTimeline.COLLAPSE_TICKS;
        }
        passOutAdvanceScheduled = true;
        com.stardew.craft.time.ServerRealTickTaskScheduler.schedule(server, delay, () -> {
            try {
                SleepVoteTracker.clearVotes();
                advanceToNextMorning(stardewLevel, sleepMinute, reason);
                teleportPlayersToFarmSpawn(server, pendingPassOutPlayers);
            } finally {
                passOutAdvanceScheduled = false;
            }
        });
    }

    private static java.util.Set<java.util.UUID> collectPendingPassOutPlayers(
            net.minecraft.server.MinecraftServer server,
            java.util.Set<java.util.UUID> candidates) {
        java.util.Set<java.util.UUID> result = new java.util.HashSet<>();
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            if (candidates.contains(sp.getUUID())
                && com.stardew.craft.player.PassOutService.hasPendingPassOutResult(sp.getUUID())) {
                result.add(sp.getUUID());
            }
        }
        return result;
    }

    private static void teleportPlayersToFarmSpawn(
            net.minecraft.server.MinecraftServer server,
            java.util.Set<java.util.UUID> playerIds) {
        if (playerIds.isEmpty()) {
            return;
        }
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            if (playerIds.contains(sp.getUUID())) {
                com.stardew.craft.player.PassOutService.teleportToFarmSpawn(sp);
            }
        }
    }

    private static boolean isSleepAnchor(ServerPlayer player, BlockPos bedPos) {
        var state = player.level().getBlockState(bedPos);
        return state.getBlock() instanceof BedBlock
                || state.is(ModBlocks.BED_1.get())
                || state.is(ModBlocks.BED_2.get());
    }

    private static void wakeSleepingStardewPlayers(net.minecraft.server.MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (isStardewDimension(player.serverLevel()) && player.isSleeping()) {
                player.stopSleepInBed(false, false);
            }
        }
    }

    /**
     * 从MC dayTime计算星露谷分钟
     * dayTime 0 = 6:00 AM = 360分钟
     * dayTime 1000 = 7:00 AM = 420分钟
     * 公式：分钟 = (dayTime / 1000.0 + 6) * 60
     */
    public static int mcTimeToStardewMinutes(long dayTime) {
        // 确保dayTime在0-24000范围内
        dayTime = dayTime % 24000;
        if (dayTime < 0) dayTime += 24000;
        
        // 计算星露谷分钟
        // dayTime / 1000 = MC小时（从6:00开始）
        // +6 是因为dayTime 0 = 6:00 AM
        // *60 转换为分钟
        return (int) ((dayTime / 1000.0 + 6) * 60);
    }
    
    /**
     * 从星露谷分钟计算MC dayTime
     * 360分钟 (6:00 AM) = dayTime 0
     * 720分钟 (12:00 PM) = dayTime 6000
     * 公式：dayTime = (分钟 / 60.0 - 6) * 1000
     */
    public static long stardewMinutesToMcTime(int stardewMinutes) {
        // 分钟 / 60 = 小时
        // -6 是因为dayTime 0 = 6:00 AM
        // *1000 转换为MC ticks
        long dayTime = (long) ((stardewMinutes / 60.0 - 6) * 1000);
        if (dayTime < 0) dayTime += 24000;
        return dayTime;
    }

    /**
     * 进入星露谷维度前校验地图是否已就绪（仅预烘焙逻辑）。
     */
    @SuppressWarnings("null")
    @SubscribeEvent
    public static void onTravelToDimension(EntityTravelToDimensionEvent event) {
        if (event.getEntity() instanceof Parrot) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!ModDimensions.STARDEW_VALLEY.equals(event.getDimension())) {
            return;
        }

        ServerLevel stardewLevel = player.server.getLevel(ModDimensions.STARDEW_VALLEY);
        if (stardewLevel == null) {
            return;
        }

        // MCA 只能在 ServerAboutToStart、维度尚未加载时安装或修复。
        // 传送阶段只做只读校验，禁止在运行中的 ServerLevel 下替换 region 文件。
        boolean prebuiltReady = StardewValleyPrebuiltRegionInstaller.hasInstalledPrebuilt(player.server);

        if (prebuiltReady) {
            StardewValleyMapBootstrap.markPrebuiltInstalled(
                stardewLevel,
                StardewValleyPrebuiltRegionInstaller.CURRENT_PREGEN_VERSION
            );
        }

        // pregen region 刚被覆盖会抹掉镇子里所有动态放置的方块（传送触发、
        // 矿车站点等）。必须重置所有管理器的 SavedData 版本号，让后续
        // onPlayerChangeDimension 中的 ensurePlaced() 重新执行放置。
        // 注意：大多数情况在 onServerStarting 中已处理。这里处理启动时维度尚未加载的极端路径，
        // 用于 onServerStarting 时 level 尚未加载的极端场景。
        if (com.stardew.craft.StardewCraft.pregenJustInstalled) {
            com.stardew.craft.StardewCraft.pregenJustInstalled = false;
            com.stardew.craft.dimension.StardewBiomePatcher.schedulePregenBiomeMigration(
                stardewLevel, "pregen_region_reinstalled_travel");
            com.stardew.craft.interior.InteriorSubspaceManager.replaceAllPortalsIfReady(
                stardewLevel, "pregen_region_reinstalled");

            // 重置农场入口 / 采石场入口 / 下水道入口 / 矿车站点的放置标记
            com.stardew.craft.farm.FarmEntryBarrierManager.get(stardewLevel).resetForMigration();
            com.stardew.craft.communitycenter.quarry.QuarryAccessManager.get(stardewLevel).resetForMigration();
            com.stardew.craft.sewer.SewerAccessManager.get(stardewLevel).resetForMigration();
            com.stardew.craft.minecart.MinecartStationManager.get(stardewLevel).resetForMigration();
            com.stardew.craft.mastery.MasterySiteInstaller.get(stardewLevel).resetForMigration();
            com.stardew.craft.statue.UncertaintyStatueInstaller.get(stardewLevel).resetForMigration();
            // 采石场初始石头也在 pregen region 范围内，覆盖后需重新铺
            com.stardew.craft.manager.QuarrySpawnService.resetInitialSpawn(stardewLevel);

            StardewCraft.LOGGER.info("[VALLEY_PREGEN] Reset all manager SavedData versions after pregen reinstall");
        }

        if (!prebuiltReady) {
            event.setCanceled(true);
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("stardewcraft.dimension.pregen_required"), false);
            StardewCraft.LOGGER.error("[VALLEY_MAP] Denied travel to Stardew Valley: prebuilt region state validation failed");
        }
    }

    /**
     * 玩家进入维度时确保昼夜循环开启
     */
    @SuppressWarnings("null")
    @SubscribeEvent
    public static void onPlayerChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (ModDimensions.STARDEW_VALLEY.equals(event.getFrom())) {
            ServerPlayer player = (ServerPlayer) event.getEntity();
            com.stardew.craft.farm.FarmChunkManager.get()
                    .onPlayerLeaveFarm(player.serverLevel(), player);
        }

        // 星露谷维度和矿井维度都需要确保昼夜循环开启
        if (ModDimensions.STARDEW_VALLEY.equals(event.getTo()) || ModMiningDimensions.STARDEW_MINING.equals(event.getTo())) {
            ServerPlayer player = (ServerPlayer) event.getEntity();
            ServerLevel level = player.serverLevel();

            // Quest: warped to location
            String location = event.getTo().location().toString();
            com.stardew.craft.quest.StardewQuestEvents.fireWarped(player, location);

            if (ModDimensions.STARDEW_VALLEY.equals(event.getTo())) {
                // 如果是 CrossDimensionTeleporter 主动传送（如巫师塔入口），不覆盖目标位置
                if (!com.stardew.craft.interior.CrossDimensionTeleporter.consumeSkipAutoTeleport(player.getUUID())) {
                    // 查询玩家的农场出生点
                    com.stardew.craft.farm.FarmInstanceRegistry registry = com.stardew.craft.farm.FarmInstanceRegistry.get();
                    net.minecraft.core.BlockPos spawnPos = registry.getFarmSpawnPoint(player.getUUID());
                    if (spawnPos == null) {
                        StardewCraft.LOGGER.warn("[DIMENSION] Player {} entered Stardew Valley without a farm spawn; skipping farm auto-teleport.",
                                player.getName().getString());
                    } else {
                        // 传送前清理
                        player.closeContainer();
                        player.stopUsingItem();

                        preloadChunksAround(level, spawnPos, 2);
                        player.teleportTo(level, spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D, player.getYRot(), player.getXRot());
                    }
                }

                com.stardew.craft.farm.FarmChunkManager.get().reconcilePlayerOccupancy(player);

                // 把各 ensurePlaced() 分散到后续 tick 执行，防止同帧堆叠触发 watchdog。
                // 每个任务本身有 SavedData 版本检查，已完成的会立即跳过（< 1ms）。
                scheduleDeferredInit(level);
            }

            // 发送星露谷虚拟时间包（不修改全局 GameRule）
            StardewTimeManager timeManager = StardewTimeManager.get();
            long virtualDayTime = timeManager.getVirtualDayTime();
            player.connection.send(new ClientboundSetTimePacket(
                level.getGameTime(), virtualDayTime, true));

            // 发送星露谷天气渲染包（覆盖原版 sendLevelInfo 发的主世界天气）
            {
                var weatherData = com.stardew.craft.weather.WeatherSavedData.get(level);
                var weatherState = weatherData.getWeatherState(level.dimension());
                com.stardew.craft.weather.WeatherManager.WeatherState.sendWeatherPackets(
                        player, weatherState.isRaining(), weatherState.isThundering());
            }
            
            // 同步玩家数据到客户端
            com.stardew.craft.player.PlayerDataEventHandler.syncPlayerData(
                player, 
                com.stardew.craft.player.PlayerDataManager.getPlayerData(player)
            );
            
            // 同步当前时间到客户端（timeManager 已在前面获取）
            PacketDistributor.sendToPlayer(player, TimeSyncPacket.fromTimeManager(timeManager));
        }

        // 玩家进入矿井维度：确保结构生成并传送到对应层数
        if (ModMiningDimensions.STARDEW_MINING.equals(event.getTo())) {
            ServerPlayer player = (ServerPlayer) event.getEntity();
            ServerLevel level = player.serverLevel();

            // 获取玩家的矿井数据
            com.stardew.craft.mining.MiningPlayerData playerData =
                com.stardew.craft.mining.MiningDataManager.getPlayerData(player);

            int currentFloor = playerData.getCurrentFloor();

            // 确保大厅已生成（固定中心 0,64,0）
            com.stardew.craft.mining.MineEntranceBootstrap.ensureGenerated(level);

            // 确保当前楼层已生成
            if (currentFloor > 0) {
                com.stardew.craft.mining.MineFloorGenerator.generateFloor(level, currentFloor);
            }

            // 如果是 CrossDimensionTeleporter 主动传送（如矿车进入矿井），不覆盖目标位置
            if (!com.stardew.craft.interior.CrossDimensionTeleporter.consumeSkipAutoTeleport(player.getUUID())) {
                // 传送到当前层数的出生点
                com.stardew.craft.mining.MiningCoordinates.teleportPlayerToFloor(player, level, currentFloor);
            }
            
            // 同步层数到客户端（显示UI）
            com.stardew.craft.network.MiningFloorSyncPacket packet = 
                new com.stardew.craft.network.MiningFloorSyncPacket(currentFloor);
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, packet);
            com.stardew.craft.event.MiningBlockBreakHandler.syncLadderStateForPlayer(player, currentFloor);

            // 首次进入矿井赠送矿洞图腾
            if (!playerData.hasReceivedMineTotem()) {
                net.minecraft.world.item.ItemStack totem = new net.minecraft.world.item.ItemStack(
                    com.stardew.craft.item.ModItems.MINE_TOTEM.get());
                if (!player.getInventory().add(totem)) {
                    player.drop(totem, false);
                }
                playerData.setReceivedMineTotem(true);
                com.stardew.craft.mining.MiningDataManager.savePlayerData(player, playerData);
                player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("item.stardewcraft.mine_totem.gift")
                        .withStyle(net.minecraft.ChatFormatting.GOLD), false);
            }

            // 延迟 3 tick 强制刷新客户端光照
            final int floor = currentFloor;
            level.getServer().tell(new net.minecraft.server.TickTask(
                level.getServer().getTickCount() + 3,
                () -> com.stardew.craft.mining.MineFloorGenerator.forceClientLightRefresh(level, floor)
            ));
        }
    }
    
    /**
     * 服务端维度Tick事件 - 从MC时间同步星露谷时间
     */
    @SuppressWarnings("null")
    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        
        // 只在星露谷维度处理（矿井维度会自动跟随）
        if (!ModDimensions.STARDEW_VALLEY.equals(serverLevel.dimension())) {
            return;
        }

        var server = serverLevel.getServer();
        boolean simulationPaused = com.stardew.craft.time.StardewTimePauseService.isPaused(server);
        boolean clockPaused = com.stardew.craft.time.StardewTimePauseService.isClockPaused(server);

        // ── 每 tick 将维度实际 dayTime 对齐到虚拟时间 ──
        // 原版 ServerLevel.tickTime() 会每 tick 递增维度自身的 dayTime，
        // 导致它与 virtualDayTime 不断漂移。若有任何代码路径
        // （sendLevelInfo / synchronizeTime 等）直接读 level.getDayTime()
        // 并发送给客户端，就会和 mod 发的虚拟时间打架 → 天空闪烁。
        // 此处强制对齐，使所有原版代码路径读到的都是正确的虚拟时间。
        StardewTimeManager timeManager = StardewTimeManager.get();
        // 星露谷时钟只由自己的暂停/节日状态控制，完全不读取主世界昼夜规则。
        if (!clockPaused
                && !com.stardew.craft.festival.ActiveFestivalHandlers.isAnyTimeFreezeActive()) {
            timeManager.advanceIndependentDayTime(com.stardew.craft.Config.TIME_SPEED_MULTIPLIER.get());
        }
        long virtualDayTime = com.stardew.craft.festival.ActiveFestivalHandlers.applyTimeFreeze(serverLevel, timeManager);
        serverLevel.setDayTime(virtualDayTime);
        ServerLevel miningLevel = server.getLevel(ModMiningDimensions.STARDEW_MINING);
        if (miningLevel != null) {
            miningLevel.setDayTime(virtualDayTime);
        }
        if (server.getTickCount() % 5 == 0) {
            com.stardew.craft.manager.AnimalGrowthManager.get(serverLevel)
                    .continueCatchUp(serverLevel);
        }

        if (simulationPaused) {
            return;
        }

        // 检查是否有玩家在星露谷相关维度
        var allPlayers = server.getPlayerList().getPlayers();
        java.util.List<ServerPlayer> stardewPlayers = new java.util.ArrayList<>();
        for (var player : allPlayers) {
            if (ModDimensions.STARDEW_VALLEY.equals(player.level().dimension())
                || ModMiningDimensions.STARDEW_MINING.equals(player.level().dimension())) {
                stardewPlayers.add(player);
            }
        }
        boolean anyPlayerInStardew = !stardewPlayers.isEmpty();
        
        // 没有玩家在星露谷相关维度时，跳过后续时间处理（UI、警告、晕倒等）
        if (!anyPlayerInStardew) {
            return;
        }

        long dayTime = virtualDayTime % 24000;
        
        // 从虚拟 MC 时间计算星露谷分钟
        int stardewMinutes = mcTimeToStardewMinutes(dayTime);
        
        // 更新TimeManager的时间（用于UI显示和其他系统）
        timeManager.setCurrentTimeFromMC(stardewMinutes);

        int tenMinuteSlot = stardewMinutes / 10;
        int timeOfDayHHMM = stardewMinutesToTimeOfDay(stardewMinutes);
        int dayKey = timeManager.getCurrentYear() * 1000 + timeManager.getCurrentSeason() * 100 + timeManager.getCurrentDay();
        if (!dayAdvancing && (dayKey != lastAnimalTenMinuteDayKey || tenMinuteSlot != lastAnimalTenMinuteSlot)) {
            lastAnimalTenMinuteDayKey = dayKey;
            lastAnimalTenMinuteSlot = tenMinuteSlot;
            com.stardew.craft.manager.AnimalGrowthManager.get(serverLevel).updatePerTenMinutes(serverLevel, timeOfDayHHMM);
        }
        
        // ── 午夜 0:00（对标 SDV case 2400）── 时钟抖动 + 困倦表情 + "It's getting late..." 消息
        if (dayTime >= MIDNIGHT_TIME && !dayAdvancing && !midnightWarned) {
            midnightWarned = true;
            for (ServerPlayer sp : stardewPlayers) {
                    // 时钟抖动 + 全局消息
                    PacketDistributor.sendToPlayer(sp, new com.stardew.craft.network.TimeWarningPayload(
                        com.stardew.craft.network.TimeWarningPayload.MIDNIGHT));
                    // 困倦表情气泡（sleep emote, iconIndex=24）
                    PacketDistributor.sendToPlayersTrackingEntityAndSelf(sp,
                        new com.stardew.craft.network.payload.EmoteBroadcastPayload(sp.getId(), 24));
            }
        }

        // ── 凌晨 1:00（对标 SDV case 2500）── 时钟抖动 + 困倦表情（无文字消息）
        if (dayTime >= ONE_AM_TIME && !dayAdvancing && !oneAMWarned) {
            oneAMWarned = true;
            for (ServerPlayer sp : stardewPlayers) {
                    PacketDistributor.sendToPlayer(sp, new com.stardew.craft.network.TimeWarningPayload(
                        com.stardew.craft.network.TimeWarningPayload.ONE_AM));
                    PacketDistributor.sendToPlayersTrackingEntityAndSelf(sp,
                        new com.stardew.craft.network.payload.EmoteBroadcastPayload(sp.getId(), 24));
            }
        }

        // ── 2:00 AM 强制晕倒 + 日推进（对标 SDV timeOfDay >= 2600）──
        if (dayTime >= DAY_END_TIME && !dayAdvancing && !passOutAdvanceScheduled) {
            // 时钟抖动
            if (!twoAMWarned) {
                twoAMWarned = true;
                for (ServerPlayer sp : stardewPlayers) {
                        PacketDistributor.sendToPlayer(sp, new com.stardew.craft.network.TimeWarningPayload(
                            com.stardew.craft.network.TimeWarningPayload.TWO_AM));
                }
            }
            // 对标 SDV case 2600: 强制下马、停止坐下、中断工具使用
            for (ServerPlayer sp : stardewPlayers) {
                    // 强制下马
                    if (sp.getVehicle() != null) {
                        sp.stopRiding();
                    }
                    // 强制停止使用物品/工具
                    if (sp.isUsingItem()) {
                        sp.stopUsingItem();
                    }
            }
            // 关键：先保存已投票玩家快照，再清空投票
            java.util.Set<java.util.UUID> votedPlayers = SleepVoteTracker.getSleepingPlayerSnapshot();
            // 1. 对每个玩家执行晕倒惩罚（跳过已投睡觉票的玩家——他们选择了睡觉，不算晕倒）
            for (ServerPlayer sp : stardewPlayers) {
                    if (!votedPlayers.contains(sp.getUUID())
                            && !com.stardew.craft.player.PassOutService.hasPendingPassOutResult(sp.getUUID())
                            && !com.stardew.craft.player.PassOutService.isKnockedOut(sp)
                            && !sp.isCreative()
                            && !sp.isSpectator()
                            && com.stardew.craft.farm.FarmInstanceRegistry.get().hasFarm(sp.getUUID())) {
                        com.stardew.craft.player.PassOutService.on2AMPassOut(sp);
                    }
            }
            // 2. 原版先完整播放 animation 293，再推进次日并回床。
            java.util.Set<java.util.UUID> transitionPlayers = new java.util.HashSet<>(votedPlayers);
            for (ServerPlayer sp : stardewPlayers) {
                if (com.stardew.craft.player.PassOutService.hasPendingPassOutResult(sp.getUUID())) {
                    transitionPlayers.add(sp.getUUID());
                }
            }
            schedulePassOutAdvance(
                    serverLevel,
                    stardewMinutes,
                    "pass_out_2am",
                    transitionPlayers);
        }
        
        // 每秒（20 ticks）同步UI时间+虚拟天空时间到客户端（仅发给星露谷维度玩家）
        if (serverLevel.getGameTime() % 20 == 0) {
            var syncPacket = TimeSyncPacket.fromTimeManager(timeManager);
            for (ServerPlayer sp : stardewPlayers) {
                PacketDistributor.sendToPlayer(sp, syncPacket);
                // 同步虚拟 dayTime 给客户端天空渲染
                sp.connection.send(new ClientboundSetTimePacket(
                    sp.serverLevel().getGameTime(), virtualDayTime, true));
            }
        }

        // 每5秒（100 ticks）验证并修复玩家附近的传送触发方块
        if (serverLevel.getGameTime() % 100 == 0) {
            for (ServerPlayer sp : stardewPlayers) {
                ServerLevel playerLevel = sp.serverLevel();
                InteriorSubspaceManager.verifyAndRepairNearby(playerLevel, sp);
            }
        }
    }
    
    /**
     * 监听睡觉完成事件。
     * 星露谷维度：触发日结算。
     * 主世界睡觉不会影响独立的星露谷时钟。
     */
    @SuppressWarnings("null")
    @SubscribeEvent
    public static void onSleepFinished(SleepFinishedTimeEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        if (level.dimension() == ModDimensions.STARDEW_VALLEY) {
            StardewTimeManager timeManager = StardewTimeManager.get();
            SleepVoteTracker.clearVotes();
            advanceToNextMorning(level, timeManager.getCurrentTime(), "vanilla_sleep_finished");
        }
    }

    /**
     * 立即更新MC时间（命令调用）— 通过 offset 隔离，不修改主世界
     */
    @SuppressWarnings("null")
    public static void updateMCTime(StardewTimeManager timeManager) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            long mcTimeOfDay = stardewMinutesToMcTime(timeManager.getCurrentTime());
            
            // 获取当前虚拟天数，用于计算新的虚拟绝对时间
            long currentVirtual = timeManager.getVirtualDayTime();
            long currentDay = currentVirtual / 24000;
            long newVirtualDayTime = currentDay * 24000 + mcTimeOfDay;
            
            // 修改 offset 而非 overworld dayTime
            timeManager.setVirtualDayTime(newVirtualDayTime);
            
            // 发送时间包给星露谷维度的玩家
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                ServerLevel playerLevel = player.serverLevel();
                if (isStardewDimension(playerLevel)) {
                    player.connection.send(new ClientboundSetTimePacket(
                        playerLevel.getGameTime(),
                        newVirtualDayTime,
                        true
                    ));
                }
            }
            
        }
    }

    private static void preloadChunksAround(ServerLevel level, net.minecraft.core.BlockPos center, int radiusChunks) {
        int centerChunkX = center.getX() >> 4;
        int centerChunkZ = center.getZ() >> 4;
        for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
            for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
                level.getChunk(centerChunkX + dx, centerChunkZ + dz);
            }
        }
    }

    // ── 分帧延迟初始化：把重方块操作分散到多个 tick，每 tick 只跑一个任务，防止 watchdog ──
    /** 标记是否已有待执行的初始化队列（避免多个玩家同时进入重复调度）。 */
    private static volatile boolean deferredInitScheduled = false;
    private static net.minecraft.server.MinecraftServer deferredInitServer;

    /**
    * 将 ensurePlaced / ensureInitialSpawn 操作排入 TickTask 队列，
    * 每 tick 执行一个，不会卡主线程。
     */
    public static void scheduleDeferredInit(ServerLevel level) {
        var server = level.getServer();
        if (deferredInitScheduled && deferredInitServer == server) {
            return;
        }
        deferredInitScheduled = true;
        deferredInitServer = server;
        int baseTick = server.getTickCount() + 1;

        // 任务 1: 农场入口触发区
        server.tell(new net.minecraft.server.TickTask(baseTick, () -> {
            try {
                ServerLevel sdv = server.getLevel(ModDimensions.STARDEW_VALLEY);
                if (sdv != null) {
                    com.stardew.craft.farm.FarmEntryBarrierManager.get(sdv).ensureBarriersPlaced(sdv);
                }
            } catch (Exception e) {
                StardewCraft.LOGGER.error("[DEFERRED_INIT] FarmEntryBarrier failed", e);
            }
        }));

        // 任务 2: 采石场传送触发
        server.tell(new net.minecraft.server.TickTask(baseTick + 1, () -> {
            try {
                ServerLevel sdv = server.getLevel(ModDimensions.STARDEW_VALLEY);
                if (sdv != null) {
                    com.stardew.craft.communitycenter.quarry.QuarryAccessManager.get(sdv).ensurePlaced(sdv);
                }
            } catch (Exception e) {
                StardewCraft.LOGGER.error("[DEFERRED_INIT] QuarryAccess failed", e);
            }
        }));

        // 任务 3: 下水道传送触发
        server.tell(new net.minecraft.server.TickTask(baseTick + 2, () -> {
            try {
                ServerLevel sdv = server.getLevel(ModDimensions.STARDEW_VALLEY);
                if (sdv != null) {
                    com.stardew.craft.sewer.SewerAccessManager.get(sdv).ensurePlaced(sdv);
                }
            } catch (Exception e) {
                StardewCraft.LOGGER.error("[DEFERRED_INIT] SewerAccess failed", e);
            }
        }));

        // 任务 4: 矿车站点实体 + 铁轨
        server.tell(new net.minecraft.server.TickTask(baseTick + 3, () -> {
            try {
                ServerLevel sdv = server.getLevel(ModDimensions.STARDEW_VALLEY);
                if (sdv != null) {
                    com.stardew.craft.minecart.MinecartStationManager.get(sdv).ensurePlaced(server);
                }
            } catch (Exception e) {
                StardewCraft.LOGGER.error("[DEFERRED_INIT] MinecartStation failed", e);
            }
        }));

        // 任务 5: 特殊订单公告板
        server.tell(new net.minecraft.server.TickTask(baseTick + 4, () -> {
            try {
                ServerLevel sdv = server.getLevel(ModDimensions.STARDEW_VALLEY);
                if (sdv != null) {
                    com.stardew.craft.specialorder.SpecialOrderBoardInstaller.get(sdv).ensurePlaced(sdv);
                }
            } catch (Exception e) {
                StardewCraft.LOGGER.error("[DEFERRED_INIT] SpecialOrderBoard failed", e);
            }
        }));

        // 任务 6: 地面采集物初始化
        server.tell(new net.minecraft.server.TickTask(baseTick + 5, () -> {
            try {
                ServerLevel sdv = server.getLevel(ModDimensions.STARDEW_VALLEY);
                if (sdv != null) {
                    int season = com.stardew.craft.time.StardewTimeManager.get().getCurrentSeason();
                    com.stardew.craft.manager.ForageSpawnService.ensureInitialSpawn(sdv, season);
                    com.stardew.craft.manager.ArtifactSpotSpawnService.ensureInitialSpawn(sdv, season);
                }
            } catch (Exception e) {
                StardewCraft.LOGGER.error("[DEFERRED_INIT] Forage/Artifact failed", e);
            }
        }));

        // 任务 5.5: 精通山洞站点（门/讲台/蜡烛/展示实体）
        server.tell(new net.minecraft.server.TickTask(baseTick + 6, () -> {
            try {
                ServerLevel sdv = server.getLevel(ModDimensions.STARDEW_VALLEY);
                if (sdv != null) {
                    com.stardew.craft.mastery.MasterySiteInstaller.get(sdv).ensurePlaced(sdv);
                    com.stardew.craft.statue.UncertaintyStatueInstaller.get(sdv).ensurePlaced(sdv);
                }
            } catch (Exception e) {
                StardewCraft.LOGGER.error("[DEFERRED_INIT] MasterySite/UncertaintyStatue failed", e);
            }
        }));

        // 任务 8: 秘密森林入口与煤森林资源
        server.tell(new net.minecraft.server.TickTask(baseTick + 7, () -> {
            try {
                ServerLevel sdv = server.getLevel(ModDimensions.STARDEW_VALLEY);
                if (sdv != null) {
                    com.stardew.craft.manager.CoalForestClumpSpawnService.ensureInitialSpawn(sdv);
                    com.stardew.craft.manager.SecretWoodsAccessManager.ensureEntranceReady(sdv);
                }
            } catch (Exception e) {
                StardewCraft.LOGGER.error("[DEFERRED_INIT] CoalForest/SecretWoods failed", e);
            }
        }));

        // 任务 9: 采石场石头铺设（强制加载区块 + 5千格扫描）
        server.tell(new net.minecraft.server.TickTask(baseTick + 8, () -> {
            try {
                ServerLevel sdv = server.getLevel(ModDimensions.STARDEW_VALLEY);
                if (sdv != null) {
                    int year = com.stardew.craft.time.StardewTimeManager.get().getCurrentYear();
                    com.stardew.craft.manager.QuarrySpawnService.ensureInitialSpawn(sdv, year);
                }
            } catch (Exception e) {
                StardewCraft.LOGGER.error("[DEFERRED_INIT] QuarrySpawn failed", e);
            }
        }));

        // 任务 10: 清除调度标记
        server.tell(new net.minecraft.server.TickTask(baseTick + 9, () -> {
            if (deferredInitServer == server) {
                deferredInitScheduled = false;
                deferredInitServer = null;
            }
            StardewCraft.LOGGER.info("[DEFERRED_INIT] All deferred init tasks completed.");
        }));
    }

    private static boolean isStardewDimension(ServerLevel level) {
        return ModDimensions.STARDEW_VALLEY.equals(level.dimension())
                || ModMiningDimensions.STARDEW_MINING.equals(level.dimension());
    }

    private static int stardewMinutesToTimeOfDay(int stardewMinutes) {
        int hour = Math.max(0, stardewMinutes / 60);
        int minute = Math.max(0, stardewMinutes % 60);
        return hour * 100 + minute;
    }
}
