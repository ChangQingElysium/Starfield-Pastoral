package com.stardew.craft.farm;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.server.performance.PerformanceCounter;
import com.stardew.craft.server.performance.PerformanceTiming;
import com.stardew.craft.server.performance.ServerPerformanceRecorder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import javax.annotation.Nullable;
import java.util.*;

/**
 * 农场区块生命周期管理器（轻量版）。
 *
 * 设计原则：**不再永久 forceLoad 农场区块**。
 * - 玩家在场 → MC 原生视距机制自然加载周围区块
 * - 玩家离开 → MC 原生机制自然卸载
 * - 作物系统使用每日结算（CropGrowthManager.growDaily），不依赖 random tick
 * - Utility 机器使用绝对时间戳比较，区块重新加载后自动补偿
 * - 离线追赶（OfflineFarmCatchUp）临时 forceLoad，完成后立即释放
 *
 * 仅保留玩家计数追踪（供其他系统查询）和临时 forceLoad 能力。
 */
public class FarmChunkManager {

    private static final FarmChunkManager INSTANCE = new FarmChunkManager();

    /** 玩家实际所在农场及各农场在场人数。 */
    private final FarmOccupancyTracker<UUID> occupancy = new FarmOccupancyTracker<>();

    /** 兼容旧 API 的农场级租约引用，按维度对象 identity 与 slot 隔离。 */
    private final IdentityHashMap<ServerLevel, Map<Integer, TemporaryFarmLoad>> temporaryFarmLoads =
            new IdentityHashMap<>();

    private final TemporaryChunkLeaseTracker<ServerLevel> temporaryChunkLeases =
            new TemporaryChunkLeaseTracker<>(new TemporaryChunkLeaseTracker.Backend<>() {
                @Override
                public boolean acquire(ServerLevel level, ChunkPos chunk) {
                    long chunkKey = chunk.toLong();
                    if (level.getForcedChunks().contains(chunkKey)) return false;
                    return level.setChunkForced(chunk.x, chunk.z, true);
                }

                @Override
                public void load(ServerLevel level, ChunkPos chunk) {
                    ServerPerformanceRecorder.increment(PerformanceCounter.FARM_SYNC_CHUNK_LOADS, 1L);
                    long startedAt = ServerPerformanceRecorder.startTiming();
                    try {
                        level.getChunk(chunk.x, chunk.z);
                    } finally {
                        ServerPerformanceRecorder.finishTiming(PerformanceTiming.FARM_SYNC_CHUNK_LOAD, startedAt);
                    }
                }

                @Override
                public void release(ServerLevel level, ChunkPos chunk) {
                    level.setChunkForced(chunk.x, chunk.z, false);
                }
            });

    private static final class TemporaryFarmLoad {
        private final TemporaryChunkLeaseTracker.Lease lease;
        private int references = 1;

        private TemporaryFarmLoad(TemporaryChunkLeaseTracker.Lease lease) {
            this.lease = lease;
        }
    }

    private FarmChunkManager() {}

    public static FarmChunkManager get() {
        return INSTANCE;
    }

    /**
     * 玩家进入农场时调用。
     * 仅追踪玩家计数，不 forceLoad（由 MC 原生视距加载）。
     */
    public void onPlayerEnterFarm(ServerLevel level, ServerPlayer player, FarmInstance farm) {
        FarmOccupancyTracker.Transition transition = occupancy.enter(player.getUUID(), farm.getSlotIndex());
        if (!transition.changed()) return;

        transition.previous().ifPresent(previous -> StardewCraft.LOGGER.debug(
                "[FARM_CHUNK] Player {} left farm slot {}, players={}",
                player.getName().getString(), previous.slot(), previous.count()));
        StardewCraft.LOGGER.debug("[FARM_CHUNK] Player {} entered farm slot {}, players={}",
                player.getName().getString(), transition.current().slot(), transition.current().count());
    }

    /** Reconciles tracked occupancy against the player's real dimension and position. */
    public void reconcilePlayerOccupancy(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        if (!ModDimensions.STARDEW_VALLEY.equals(level.dimension())) {
            onPlayerLeaveFarm(level, player);
            return;
        }

        FarmInstanceRegistry registry = FarmInstanceRegistry.get();
        UUID owner = registry.getOwnerAt(player.blockPosition());
        FarmInstance farm = owner == null ? null : registry.getFarm(owner);
        if (farm == null || !farm.contains(player.blockPosition())) {
            onPlayerLeaveFarm(level, player);
            return;
        }
        onPlayerEnterFarm(level, player, farm);
    }

    /**
     * 玩家离开农场时调用。
     * 仅减少玩家计数。
     */
    public void onPlayerLeaveFarm(ServerLevel level, ServerPlayer player) {
        occupancy.leave(player.getUUID()).ifPresent(transition -> StardewCraft.LOGGER.debug(
                "[FARM_CHUNK] Player {} left farm slot {}, players={}",
                player.getName().getString(), transition.current().slot(), transition.current().count()));
    }

    /** 兼容旧调用方；实际离开的 slot 以玩家记录为准。 */
    public void onPlayerLeaveFarm(ServerLevel level, ServerPlayer player, FarmInstance farm) {
        onPlayerLeaveFarm(level, player);
    }

    /**
     * 每 tick 调用（保留接口兼容，当前为空操作）。
     */
    public void tick(ServerLevel level) {
        // 不再需要处理延迟卸载——区块由 MC 原生视距管理
    }

    // ══════════════════════════════════════════
    //  临时 forceLoad（仅用于离线追赶等一次性操作）
    // ══════════════════════════════════════════

    TemporaryChunkLeaseTracker.Lease acquireTemporaryChunks(ServerLevel level, Collection<ChunkPos> chunks) {
        return temporaryChunkLeases.acquire(level, chunks);
    }

    /** 获取一份临时农场区块租约，供离线追赶和每日结算使用。 */
    public void acquireTemporaryFarmChunks(ServerLevel level, int slotIndex) {
        Map<Integer, TemporaryFarmLoad> loadsForLevel = temporaryFarmLoads.get(level);
        TemporaryFarmLoad existing = loadsForLevel == null ? null : loadsForLevel.get(slotIndex);
        if (existing != null) {
            existing.references++;
            return;
        }

        UUID owner = FarmInstanceRegistry.get().getOwnerBySlot(slotIndex);
        if (owner == null) return;
        FarmInstance farm = FarmInstanceRegistry.get().getFarm(owner);
        if (farm == null) return;

        Set<ChunkPos> farmChunks = chunkPositionsForBounds(farm.getFarmBoundsMin(), farm.getFarmBoundsMax());
        TemporaryChunkLeaseTracker.Lease lease = acquireTemporaryChunks(level, farmChunks);
        temporaryFarmLoads.computeIfAbsent(level, ignored -> new HashMap<>())
                .put(slotIndex, new TemporaryFarmLoad(lease));
    }

    /** 释放一份临时农场区块租约。 */
    public void releaseTemporaryFarmChunks(ServerLevel level, int slotIndex) {
        Map<Integer, TemporaryFarmLoad> loadsForLevel = temporaryFarmLoads.get(level);
        if (loadsForLevel == null) return;

        TemporaryFarmLoad load = loadsForLevel.get(slotIndex);
        if (load == null || --load.references > 0) return;

        loadsForLevel.remove(slotIndex, load);
        if (loadsForLevel.isEmpty()) temporaryFarmLoads.remove(level);
        load.lease.close();
    }

    /** 兼容旧调用方。 */
    public void forceLoadFarmChunksForCatchUp(ServerLevel level, int slotIndex) {
        acquireTemporaryFarmChunks(level, slotIndex);
    }

    /** 兼容旧调用方。 */
    public void releaseTempChunks(ServerLevel level, int slotIndex) {
        releaseTemporaryFarmChunks(level, slotIndex);
    }

    static Set<ChunkPos> chunkPositionsForBounds(BlockPos min, BlockPos max) {
        int minCX = Math.min(min.getX(), max.getX()) >> 4;
        int maxCX = Math.max(min.getX(), max.getX()) >> 4;
        int minCZ = Math.min(min.getZ(), max.getZ()) >> 4;
        int maxCZ = Math.max(min.getZ(), max.getZ()) >> 4;
        Set<ChunkPos> chunks = new HashSet<>();
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                chunks.add(new ChunkPos(cx, cz));
            }
        }
        return chunks;
    }

    // ══════════════════════════════════════════
    //  查询 & 清理
    // ══════════════════════════════════════════

    /**
     * 玩家下线时清理计数。
     */
    public void onPlayerLogout(ServerLevel level, ServerPlayer player) {
        onPlayerLeaveFarm(level, player);
    }

    /**
     * 判断某个农场当前是否有玩家在场。
     */
    public boolean isFarmLoaded(int slotIndex) {
        return occupancy.isOccupied(slotIndex);
    }

    /**
     * 获取农场在场玩家数。
     */
    public int getPlayerCount(int slotIndex) {
        return occupancy.count(slotIndex);
    }

    /**
     * 服务器关闭时释放所有临时 forceLoad。
     */
    public void onServerStopping(@Nullable ServerLevel level) {
        Map<Integer, TemporaryFarmLoad> loadsForLevel = level == null ? null : temporaryFarmLoads.remove(level);
        try {
            if (loadsForLevel != null) {
                for (TemporaryFarmLoad load : loadsForLevel.values()) {
                    load.lease.close();
                }
            }
        } finally {
            try {
                temporaryChunkLeases.closeAll();
            } finally {
                temporaryFarmLoads.clear();
                occupancy.clear();
            }
        }
        StardewCraft.LOGGER.info("[FARM_CHUNK] Cleanup on server stop");
    }
}
