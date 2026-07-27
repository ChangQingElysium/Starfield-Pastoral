package com.stardew.craft.manager;

import com.stardew.craft.api.v1.agriculture.StardewCropRemovalCause;
import com.stardew.craft.api.v1.agriculture.StardewCropRuntime;
import com.stardew.craft.api.v1.agriculture.StardewCropState;
import com.stardew.craft.block.crop.StardewCropBlock;
import com.stardew.craft.entity.passive.CrowEntity;
import com.stardew.craft.farm.FarmInstance;
import com.stardew.craft.farm.FarmInstanceRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 乌鸦袭击调度器（每日清晨结算时调用）。
 * Stardew 原版 Crop/Farm.addCrows 行为：
 *   - 仅扫描已进入中期生长（phase>=2）且未枯萎的作物；
 *   - potentialCrows = min(4, count/16)；
 *   - 每只乌鸦 30% 几率攻击；
 *   - 命中位置若被稻草人覆盖 → 稻草人 crowsScared++；
 *   - 否则销毁该作物 + 在该处生成 CrowEntity（30s 自销毁）。
 *
 * 多人服务器可见性：优先从玩家视距内（已加载区块）挑目标，
 * 保证清晨起床能看到乌鸦落下；没有已加载格时仍按原池抽，作物照样被吃。
 *
 * 性能：仅处理在线农场；按 FarmInstance 分别采样；位置去重避免重复处理同一格。
 */
public final class CrowAttackScheduler {
    private CrowAttackScheduler() {}

    public static void processOvernight(ServerLevel stardewLevel) {
        if (stardewLevel == null) return;

        FarmInstanceRegistry registry = FarmInstanceRegistry.get();
        Set<UUID> onlineOwners;
        try {
            onlineOwners = com.stardew.craft.farm.FarmDailyProcessHelper.getOnlineFarmOwners(stardewLevel);
        } catch (Throwable t) {
            onlineOwners = Collections.emptySet();
        }

        CropGrowthManager cropMgr = CropGrowthManager.get(stardewLevel);
        ScarecrowManager scareMgr = ScarecrowManager.get(stardewLevel);
        net.minecraft.util.RandomSource rng = stardewLevel.random;

        // 提前快照所有作物位置
        List<GlobalPos> allCrops = cropMgr.getAllCropPositions();
        if (allCrops.isEmpty()) return;

        for (FarmInstance farm : registry.getAllFarms()) {
            if (farm == null) continue;
            // 离线优化：仅处理"任一成员在线"的农场
            if (!onlineOwners.isEmpty()) {
                if (!onlineOwners.contains(farm.getOwnerUUID())) continue;
            }

            BlockPos minB = farm.getFarmBoundsMin();
            BlockPos maxB = farm.getFarmBoundsMax();

            // 收集该农场内"进入中期生长"的作物（phase ≥ 2，对齐 SDV Farm.addCrows）
            List<BlockPos> ripeInFarm = new ArrayList<>();
            for (GlobalPos gp : allCrops) {
                if (gp.dimension() != stardewLevel.dimension()) continue;
                BlockPos p = gp.pos();
                if (p.getX() < minB.getX() || p.getX() > maxB.getX()
                        || p.getY() < minB.getY() || p.getY() > maxB.getY()
                        || p.getZ() < minB.getZ() || p.getZ() > maxB.getZ()) {
                    continue;
                }
                StardewCropState crop = StardewCropRuntime.inspect(stardewLevel, p);
                if (crop == null || !crop.root().equals(p)) {
                    continue;
                }
                CropGrowthManager.CropGrowthState st = cropMgr.getState(stardewLevel, p);
                boolean coreMidGrowth = st != null && st.phase > 1;
                boolean addonMidGrowth =
                        !(stardewLevel.getBlockState(p).getBlock()
                                instanceof StardewCropBlock)
                                && crop.visualStage() > 1;
                if (coreMidGrowth || addonMidGrowth) ripeInFarm.add(p);
            }
            if (ripeInFarm.isEmpty()) continue;

            int potentialCrows = Math.min(4, ripeInFarm.size() / 16);
            if (potentialCrows < 1) continue;

            // 优先从"玩家视距内已加载区块"里挑目标——这样清晨起床能真看到乌鸦落下。
            // 如果一格已加载作物都没有（远端孤岛田），退回全集合，作物照样被吃（SDV parity）。
            List<BlockPos> loadedCandidates = new ArrayList<>();
            for (BlockPos p : ripeInFarm) {
                if (stardewLevel.isLoaded(p)) loadedCandidates.add(p);
            }
            List<BlockPos> targetPool = loadedCandidates.isEmpty() ? ripeInFarm : loadedCandidates;

            Set<BlockPos> alreadyTargeted = new HashSet<>();
            for (int i = 0; i < potentialCrows; i++) {
                if (rng.nextFloat() >= 0.30F) continue;

                BlockPos target = targetPool.get(rng.nextInt(targetPool.size()));
                if (!alreadyTargeted.add(target)) continue;

                BlockPos covering = scareMgr.findScarecrowCovering(target);
                if (covering != null) {
                    // 被稻草人保护：通知 BE 计数
                    ScarecrowManager.notifyScarecrowScared(stardewLevel, covering);
                    continue;
                }

                // 销毁作物 + 生成乌鸦。即便 target 所在 chunk 未加载，setBlock 会按需短暂加载，
                // 开销在每农场/天 ≤4 次级别，完全可忽略，并且与 SDV 行为一致（作物必被吃掉）。
                StardewCropRuntime.remove(
                        stardewLevel, target, StardewCropRemovalCause.CROW);
                try {
                    CrowEntity.spawnAt(stardewLevel, target);
                } catch (Throwable t) {
                    com.stardew.craft.StardewCraft.LOGGER.warn(
                            "Failed to spawn CrowEntity at {}: {}", target, t.toString());
                }
            }
        }
    }
}
