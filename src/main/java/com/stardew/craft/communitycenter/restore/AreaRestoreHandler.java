package com.stardew.craft.communitycenter.restore;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.interior.InteriorSubspaceManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * 执行社区中心区域方块替换：从 refurbished 缓存中取出目标区域的方块，
 * 逐个覆盖到世界中（ruins → refurbished）。
 * <p>
 * 对于没有独立房间边界的区域（鱼缸、布告栏），跳过方块替换——
 * 它们属于"公共区域"，在全部完成时统一修复。
 */
@SuppressWarnings("null")
public final class AreaRestoreHandler {

    private AreaRestoreHandler() {}

    /**
     * 修复指定区域：从 refurbished 缓存覆盖方块。
     *
     * @param level  服务端世界
     * @param areaId 区域 ID (0-5)
     * @return 替换的方块数量，-1 表示失败
     */
    public static int restoreArea(ServerLevel level, int areaId) {
        return restoreArea(level, areaId, null);
    }

    /**
     * Per-player 版本，指定 CC 原点。
     */
    public static int restoreArea(ServerLevel level, int areaId, BlockPos ccOrigin) {
        CCAreaRegistry.AreaBounds bounds = CCAreaRegistry.getArea(areaId);
        if (bounds == null) {
            StardewCraft.LOGGER.warn("[CC] Unknown area ID for restore: {}", areaId);
            return -1;
        }

        // 没有独立房间边界的区域跳过方块替换 (由 restoreAllRemaining 处理)
        if (!bounds.hasRoomBounds()) {
            StardewCraft.LOGGER.info("[CC] Area {} has no room bounds, skipping block restore", areaId);
            return 0;
        }

        BlockPos minWorld = ccOrigin != null ? bounds.boundsMinWorld(ccOrigin) : bounds.boundsMinWorld();
        BlockPos maxWorld = ccOrigin != null ? bounds.boundsMaxWorld(ccOrigin) : bounds.boundsMaxWorld();
        BlockPos origin = ccOrigin != null ? ccOrigin : InteriorSubspaceManager.CC_ORIGIN;
        return restoreRegion(level, minWorld, maxWorld, origin);
    }

    /**
     * 修复整个 CC 结构中剩余的所有方块 (全部完成后调用)。
     * 将整个 ruins 替换为 refurbished。
     */
    public static int restoreAllRemaining(ServerLevel level) {
        return restoreAllRemaining(level, null);
    }

    public static int restoreAllRemaining(ServerLevel level, BlockPos ccOrigin) {
        BlockPos origin = ccOrigin != null ? ccOrigin : InteriorSubspaceManager.CC_ORIGIN;
        CCRefurbishedCache cache = CCRefurbishedCache.get();
        if (cache.getWidth() == 0) return -1;

        BlockPos min = origin;
        BlockPos max = origin.offset(cache.getWidth() - 1, cache.getHeight() - 1, cache.getLength() - 1);
        return restoreRegion(level, min, max, origin);
    }

    private static int restoreRegion(ServerLevel level, @Nullable BlockPos worldMin, @Nullable BlockPos worldMax, BlockPos origin) {
        if (worldMin == null || worldMax == null) return -1;

        CCRefurbishedCache cache = CCRefurbishedCache.get();
        if (cache.getWidth() == 0) {
            StardewCraft.LOGGER.error("[CC] Refurbished cache not loaded, cannot restore");
            return -1;
        }

        int count = 0;

        // 确保 min/max 正确排列
        int minX = Math.min(worldMin.getX(), worldMax.getX());
        int minY = Math.min(worldMin.getY(), worldMax.getY());
        int minZ = Math.min(worldMin.getZ(), worldMax.getZ());
        int maxX = Math.max(worldMin.getX(), worldMax.getX());
        int maxY = Math.max(worldMin.getY(), worldMax.getY());
        int maxZ = Math.max(worldMin.getZ(), worldMax.getZ());

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    int rx = x - origin.getX();
                    int ry = y - origin.getY();
                    int rz = z - origin.getZ();
                    BlockState newState = cache.getBlock(rx, ry, rz);
                    if (newState == null) continue;

                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState current = level.getBlockState(pos);
                    if (!current.equals(newState)) {
                        // 结构翻新不是玩家破坏；禁止多格装饰的 extension 安全网生成掉落物。
                        MapDecorStaticBlock.runWithDropsSuppressed(() -> level.setBlock(pos, newState, 2));
                        count++;
                    }

                    // 恢复 BlockEntity 数据 (地板/墙纸纹理等)
                    CompoundTag beTag = cache.getBlockEntityTag(rx, ry, rz);
                    if (beTag != null) {
                        CompoundTag normalized = beTag.copy();
                        if (!normalized.contains("id", Tag.TAG_STRING) && normalized.contains("Id", Tag.TAG_STRING)) {
                            normalized.putString("id", normalized.getString("Id"));
                        }
                        normalized.putInt("x", x);
                        normalized.putInt("y", y);
                        normalized.putInt("z", z);
                        if (normalized.contains("id", Tag.TAG_STRING)) {
                            BlockEntity be = BlockEntity.loadStatic(pos, level.getBlockState(pos), normalized, level.registryAccess());
                            if (be != null) {
                                level.setBlockEntity(be);
                                be.setChanged();
                            }
                        }
                    }
                }
            }
        }

        // 批量通知客户端方块更新
        if (count > 0) {
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3);
                    }
                }
            }
        }

        StardewCraft.LOGGER.info("[CC] Restored region [{} ~ {}]: {} blocks replaced", worldMin, worldMax, count);
        return count;
    }
}
