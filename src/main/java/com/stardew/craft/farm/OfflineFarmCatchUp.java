package com.stardew.craft.farm;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.crop.StardewCropBlock;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.manager.CropGrowthManager;
import com.stardew.craft.manager.SprinklerManager;
import com.stardew.craft.manager.TreeGrowthManager;
import com.stardew.craft.server.performance.PerformanceCounter;
import com.stardew.craft.server.performance.PerformanceTiming;
import com.stardew.craft.server.performance.ServerPerformanceRecorder;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.UUID;

/**
 * 离线农场追赶系统。
 * <p>
 * 玩家上线时，根据离线天数批量推进其农场中的作物/树苗生长、洒水器浇水等。
 * 不精确回放每一天——直接批量计算最终状态。
 */
public final class OfflineFarmCatchUp {

    private OfflineFarmCatchUp() {}

    /**
     * 计算当前绝对天数（从第1年春1日=1开始计）。
     */
    public static int computeAbsoluteDay() {
        StardewTimeManager tm = StardewTimeManager.get();
        return (tm.getCurrentYear() - 1) * 112 + tm.getCurrentSeason() * 28 + tm.getCurrentDay();
    }

    /**
     * 计算某个绝对天数对应的季节 (0=春,1=夏,2=秋,3=冬)。
     */
    public static int seasonOfAbsDay(int absDay) {
        return ((absDay - 1) / 28) % 4;
    }

    /**
     * 玩家上线时调用。根据离线天数批量推进农场状态。
     *
     * @param level      stardew_valley 维度
     * @param playerUUID 玩家 UUID
     */
    public static void catchUp(ServerLevel level, UUID playerUUID) {
        if (level.dimension() != ModDimensions.STARDEW_VALLEY) return;

        FarmInstanceRegistry registry = FarmInstanceRegistry.get();
        FarmInstance farm = registry.getFarmForPlayer(playerUUID);
        if (farm == null || !farm.isInitialized()) return;

        int currentAbsDay = computeAbsoluteDay();
        int lastAbsDay = farm.getLastOnlineDay();
        int daysMissed = currentAbsDay - lastAbsDay;

        if (daysMissed <= 0) return;

        long startedAt = ServerPerformanceRecorder.startTiming();
        try {
            StardewCraft.LOGGER.info("[FARM-CATCHUP] Player {} missed {} days (absDay {} → {})",
                    playerUUID, daysMissed, lastAbsDay, currentAbsDay);

            // 跨季宽限期：离线期间季节变化了 → 先授予宽限天数，再追赶生长
            // （必须在 catchUpCrops 之前设置，否则 growCropOneDay 会杀掉过季作物）
            StardewTimeManager tm = StardewTimeManager.get();
            int currentSeason = tm.getCurrentSeason();
            if (farm.getLastOnlineSeason() != currentSeason) {
                int graceDays = level.getServer().getGameRules()
                        .getInt(com.stardew.craft.core.ModGameRules.RULE_CROP_GRACE_PERIOD_DAYS);
                if (graceDays > 0) {
                    farm.setGraceDaysLeft(graceDays);
                    StardewCraft.LOGGER.info(
                            "[FARM-CATCHUP] Season changed ({} → {}), granting {} grace days to player {}",
                            farm.getLastOnlineSeason(), currentSeason, graceDays, playerUUID);

                    // 通知玩家
                    net.minecraft.server.level.ServerPlayer player =
                            level.getServer().getPlayerList().getPlayer(playerUUID);
                    if (player != null) {
                        player.sendSystemMessage(
                                net.minecraft.network.chat.Component.translatable(
                                        "stardewcraft.farm.grace_period.granted", graceDays));
                    }
                }
            }

            CropGrowthManager cropMgr = CropGrowthManager.get(level);
            TreeGrowthManager treeMgr = TreeGrowthManager.get(level);
            SprinklerManager sprMgr = SprinklerManager.get(level);
            OfflineFarmCatchUpPlan plan = OfflineFarmCatchUpPlan.create(
                    level.dimension(),
                    farm.getFarmBoundsMin(),
                    farm.getFarmBoundsMax(),
                    cropMgr.getAllCropPositions(),
                    treeMgr.getAllSaplingPositions(),
                    sprMgr.getAllSprinklerPositions());
            ServerPerformanceRecorder.increment(PerformanceCounter.FARM_CATCH_UP_CHUNKS,
                    plan.requiredChunks().size());
            ServerPerformanceRecorder.increment(PerformanceCounter.FARM_CATCH_UP_OBJECTS,
                    (long) plan.crops().size() + plan.trees().size() + plan.sprinklers().size());

            try (TemporaryChunkLeaseTracker.Lease ignored =
                         FarmChunkManager.get().acquireTemporaryChunks(level, plan.requiredChunks())) {
                // 1. 批量推进作物生长
                catchUpCrops(level, cropMgr, plan.crops(), daysMissed);

                // 2. 批量推进树苗生长
                catchUpTrees(level, treeMgr, plan.trees(), daysMissed);

                // 3. 洒水器浇水（标记为已浇水状态）
                catchUpSprinklers(level, plan.sprinklers());
            }

            // 更新最后在线信息
            farm.setLastOnlineDay(currentAbsDay);
            farm.setLastOnlineSeason(currentSeason);
            registry.setDirty();

            StardewCraft.LOGGER.info("[FARM-CATCHUP] Catch-up complete for player {}", playerUUID);
        } finally {
            ServerPerformanceRecorder.finishTiming(PerformanceTiming.OFFLINE_FARM_CATCH_UP, startedAt);
        }
    }

    /**
     * 批量推进作物生长 N 天。
     * 假设洒水器每天都浇水（离线期间）。
     */
    private static void catchUpCrops(
            ServerLevel level,
            CropGrowthManager cropMgr,
            List<GlobalPos> farmCrops,
            int daysMissed) {
        if (farmCrops.isEmpty()) return;

        StardewCraft.LOGGER.info("[FARM-CATCHUP] Processing {} crops for {} days",
                farmCrops.size(), daysMissed);

        for (GlobalPos gp : farmCrops) {
            BlockPos pos = gp.pos();
            if (!level.isLoaded(pos)) continue;

            for (int d = 0; d < daysMissed; d++) {
                BlockState state = level.getBlockState(pos);
                Block block = state.getBlock();
                if (!(block instanceof StardewCropBlock cropBlock)) break;

                CropGrowthManager.CropGrowthState growthState =
                        cropMgr.getOrCreateGrowthState(gp);

                // 离线期间假设洒水器正常工作 → watered=true
                cropBlock.growCropOneDay(level, pos, state, true, growthState);
                cropMgr.setDirty();
            }
        }
    }

    /**
     * 批量推进树苗生长。
     * 直接增加 daysGrown 计数器并检查成熟。
     */
    private static void catchUpTrees(
            ServerLevel level,
            TreeGrowthManager treeMgr,
            List<GlobalPos> farmTrees,
            int daysMissed) {
        if (farmTrees.isEmpty()) return;

        StardewCraft.LOGGER.info("[FARM-CATCHUP] Processing {} tree saplings for {} days",
                farmTrees.size(), daysMissed);

        // 树苗支持 growBy — 单次解析推进 daysMissed 天，避免 N 次状态机回放
        for (GlobalPos gp : farmTrees) {
            BlockPos pos = gp.pos();
            if (!level.isLoaded(pos)) continue;

            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof com.stardew.craft.block.tree.WildTreeSaplingBlock)) {
                continue;
            }
            treeMgr.growBy(level, pos, daysMissed);
        }
    }

    /**
     * 对洒水器覆盖范围重新浇水。
     */
    private static void catchUpSprinklers(ServerLevel level, List<GlobalPos> farmSprinklers) {
        if (farmSprinklers.isEmpty()) return;

        for (GlobalPos gp : farmSprinklers) {
            BlockPos pos = gp.pos();
            if (!level.isLoaded(pos)) continue;

            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof com.stardew.craft.block.utility.SprinklerBlock sprinkler) {
                  com.stardew.craft.block.utility.SprinklerBlock.waterNow(level, pos, sprinkler.getTier());
            }
        }
    }
}
