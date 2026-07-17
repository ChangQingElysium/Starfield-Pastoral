package com.stardew.craft.farm;

import com.stardew.craft.StardewCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

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

    /** 每个农场当前在场玩家数（供其他系统查询） */
    private final Map<Integer, Integer> playerCounts = new HashMap<>();

    /**
     * 临时农场区块租约。引用计数允许登录追赶与每日结算复用同一套加载逻辑，
     * newlyForcedChunks 只记录本管理器新增的强加载，释放时不会破坏外部票据。
     */
    private final Map<Integer, TemporaryFarmLoad> temporaryFarmLoads = new HashMap<>();

    private static final class TemporaryFarmLoad {
        private final Set<ChunkPos> newlyForcedChunks;
        private int references = 1;

        private TemporaryFarmLoad(Set<ChunkPos> newlyForcedChunks) {
            this.newlyForcedChunks = newlyForcedChunks;
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
        int slot = farm.getSlotIndex();
        playerCounts.merge(slot, 1, Integer::sum);

        StardewCraft.LOGGER.debug("[FARM_CHUNK] Player {} entered farm slot {}, players={}",
                player.getName().getString(), slot, playerCounts.getOrDefault(slot, 0));
    }

    /**
     * 玩家离开农场时调用。
     * 仅减少玩家计数。
     */
    public void onPlayerLeaveFarm(ServerLevel level, ServerPlayer player, FarmInstance farm) {
        int slot = farm.getSlotIndex();
        int count = playerCounts.getOrDefault(slot, 1) - 1;
        if (count <= 0) {
            playerCounts.remove(slot);
            StardewCraft.LOGGER.debug("[FARM_CHUNK] No players in farm slot {}", slot);
        } else {
            playerCounts.put(slot, count);
        }
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

    /** 获取一份临时农场区块租约，供离线追赶和每日结算使用。 */
    public void acquireTemporaryFarmChunks(ServerLevel level, int slotIndex) {
        TemporaryFarmLoad existing = temporaryFarmLoads.get(slotIndex);
        if (existing != null) {
            existing.references++;
            return;
        }

        UUID owner = FarmInstanceRegistry.get().getOwnerBySlot(slotIndex);
        if (owner == null) return;
        FarmInstance farm = FarmInstanceRegistry.get().getFarm(owner);
        if (farm == null) return;

        Set<ChunkPos> farmChunks = chunkPositionsForBounds(farm.getFarmBoundsMin(), farm.getFarmBoundsMax());
        Set<ChunkPos> newlyForced = new HashSet<>();
        TemporaryFarmLoad load = new TemporaryFarmLoad(newlyForced);
        temporaryFarmLoads.put(slotIndex, load);

        try {
            for (ChunkPos chunk : farmChunks) {
                long chunkKey = chunk.toLong();
                if (!level.getForcedChunks().contains(chunkKey)) {
                    level.setChunkForced(chunk.x, chunk.z, true);
                    newlyForced.add(chunk);
                }
                // 强加载只添加票据；日结算在当前 tick 就要读方块，因此同步取到区块。
                level.getChunk(chunk.x, chunk.z);
            }
        } catch (RuntimeException exception) {
            temporaryFarmLoads.remove(slotIndex, load);
            for (ChunkPos chunk : newlyForced) {
                level.setChunkForced(chunk.x, chunk.z, false);
            }
            throw exception;
        }

        StardewCraft.LOGGER.debug("[FARM_CHUNK] Temporarily loaded {} farm chunks ({} newly forced, slot {})",
                farmChunks.size(), newlyForced.size(), slotIndex);
    }

    /** 释放一份临时农场区块租约。 */
    public void releaseTemporaryFarmChunks(ServerLevel level, int slotIndex) {
        TemporaryFarmLoad load = temporaryFarmLoads.get(slotIndex);
        if (load == null || --load.references > 0) return;

        temporaryFarmLoads.remove(slotIndex, load);

        for (ChunkPos cp : load.newlyForcedChunks) {
            level.setChunkForced(cp.x, cp.z, false);
        }
        StardewCraft.LOGGER.debug("[FARM_CHUNK] Released {} temporary farm chunk tickets for slot {}",
                load.newlyForcedChunks.size(), slotIndex);
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
        FarmInstance farm = FarmInstanceRegistry.get().getFarmForPlayer(player.getUUID());
        if (farm == null) return;
        int slot = farm.getSlotIndex();
        if (playerCounts.containsKey(slot)) {
            onPlayerLeaveFarm(level, player, farm);
        }
    }

    /**
     * 判断某个农场当前是否有玩家在场。
     */
    public boolean isFarmLoaded(int slotIndex) {
        return playerCounts.containsKey(slotIndex);
    }

    /**
     * 获取农场在场玩家数。
     */
    public int getPlayerCount(int slotIndex) {
        return playerCounts.getOrDefault(slotIndex, 0);
    }

    /**
     * 服务器关闭时释放所有临时 forceLoad。
     */
    public void onServerStopping(ServerLevel level) {
        for (TemporaryFarmLoad load : temporaryFarmLoads.values()) {
            for (ChunkPos cp : load.newlyForcedChunks) {
                level.setChunkForced(cp.x, cp.z, false);
            }
        }
        temporaryFarmLoads.clear();
        playerCounts.clear();
        StardewCraft.LOGGER.info("[FARM_CHUNK] Cleanup on server stop");
    }
}
