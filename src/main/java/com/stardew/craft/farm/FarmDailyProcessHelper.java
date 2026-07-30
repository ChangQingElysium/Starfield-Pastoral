package com.stardew.craft.farm;

import com.stardew.craft.core.FarmAreaResolver;
import com.stardew.craft.server.performance.PerformanceCounter;
import com.stardew.craft.server.performance.PerformanceTiming;
import com.stardew.craft.server.performance.ServerPerformanceRecorder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 农场相关 SavedData 日处理工具。
 * 核心原则：「只处理在线玩家的农场 + 公共区域」。
 *
 * 位置数据天然按坐标区分（因为每个农场在不同的网格槽位），
 * 不需要将 Map 改为 per-UUID 嵌套结构。
 * 只需要在每日处理时过滤掉离线玩家的位置。
 */
public final class FarmDailyProcessHelper {

    private FarmDailyProcessHelper() {}

    /** 日结算期间缓存的在线玩家 UUID 集合，避免对每个位置线性搜索玩家列表 */
    private static Set<UUID> cachedOnlinePlayers;
    /** 已在本次结算中确认加载的区块。 */
    private static Set<Long> cachedEnsuredChunks;
    /** 本次结算新增的强加载票据；结束时只释放这些票据。 */
    private static Set<ChunkPos> cachedNewlyForcedChunks;
    private static long dailyProcessStartedAt;

    /**
     * 日结算开始前调用，预计算在线玩家集合。
     */
    public static void beginDailyProcess(ServerLevel level) {
        dailyProcessStartedAt = ServerPerformanceRecorder.startTiming();
        cachedOnlinePlayers = new HashSet<>();
        cachedEnsuredChunks = new HashSet<>();
        cachedNewlyForcedChunks = new HashSet<>();
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            cachedOnlinePlayers.add(player.getUUID());
        }

        try {
            // 递减在线玩家农场的跨季宽限倒计时
            tickGracePeriods(level);
        } catch (RuntimeException exception) {
            endDailyProcess(level);
            throw exception;
        }
    }

    /**
     * 日结算结束后调用，释放缓存。
     */
    public static void endDailyProcess(ServerLevel level) {
        long startedAt = dailyProcessStartedAt;
        dailyProcessStartedAt = 0L;
        RuntimeException releaseFailure = null;
        try {
            if (cachedNewlyForcedChunks != null) {
                for (ChunkPos chunk : cachedNewlyForcedChunks) {
                    try {
                        level.setChunkForced(chunk.x, chunk.z, false);
                    } catch (RuntimeException exception) {
                        if (releaseFailure == null) {
                            releaseFailure = exception;
                        } else {
                            releaseFailure.addSuppressed(exception);
                        }
                    }
                }
            }
        } finally {
            cachedNewlyForcedChunks = null;
            cachedEnsuredChunks = null;
            cachedOnlinePlayers = null;
            ServerPerformanceRecorder.finishTiming(PerformanceTiming.FARM_DAILY_PROCESS, startedAt);
        }
        if (releaseFailure != null) throw releaseFailure;
    }

    /**
     * 判断某个位置是否应该在本次日处理中被处理。
     * - 公共区域位置 → 始终处理
     * - 玩家农场位置 → 仅当该玩家在线时处理
     *
     * @return true 表示应该处理
     */
    public static boolean shouldProcessPosition(ServerLevel level, BlockPos pos) {
        // 不在农场实例区域 → 公共区域，始终处理
        if (!FarmInstanceAllocator.isInFarmInstanceRegion(pos)) return true;

        UUID owner = FarmAreaResolver.getOwnerAt(pos);
        if (owner == null) return false; // 在实例区域但无人拥有

        // 检查 owner 或任一成员是否在线
        FarmInstance farm = FarmInstanceRegistry.get().getFarm(owner);
        if (farm == null) return false;
        if (cachedOnlinePlayers != null) {
            for (UUID farmer : farm.getAllFarmers()) {
                if (cachedOnlinePlayers.contains(farmer)) {
                    ensurePositionNeighborhoodLoaded(level, pos, 8);
                    return true;
                }
            }
            return false;
        }
        for (UUID farmer : farm.getAllFarmers()) {
            if (level.getServer().getPlayerList().getPlayer(farmer) != null) return true;
        }
        return false;
    }

    /** 判断某个农场成员所属农场是否应参与本次日结算。 */
    public static boolean shouldProcessFarmForPlayer(ServerLevel level, UUID playerId) {
        FarmInstance farm = FarmInstanceRegistry.get().getFarmForPlayer(playerId);
        if (farm == null) return false;
        for (UUID farmer : farm.getAllFarmers()) {
            if (cachedOnlinePlayers != null
                    ? cachedOnlinePlayers.contains(farmer)
                    : level.getServer().getPlayerList().getPlayer(farmer) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * 日结算期间按需同步加载一个对象所在区块。不会加载整张 300×300 以上的农场地图。
     */
    public static void ensurePositionLoaded(ServerLevel level, BlockPos pos) {
        if (cachedEnsuredChunks == null || cachedNewlyForcedChunks == null) return;
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        long chunkKey = ChunkPos.asLong(chunkX, chunkZ);
        if (!cachedEnsuredChunks.add(chunkKey)) return;

        if (!level.getForcedChunks().contains(chunkKey)) {
            level.setChunkForced(chunkX, chunkZ, true);
            cachedNewlyForcedChunks.add(new ChunkPos(chunkX, chunkZ));
        }
        ServerPerformanceRecorder.increment(PerformanceCounter.DAILY_SYNC_CHUNK_LOADS, 1L);
        long startedAt = ServerPerformanceRecorder.startTiming();
        try {
            level.getChunk(chunkX, chunkZ);
        } finally {
            ServerPerformanceRecorder.finishTiming(PerformanceTiming.DAILY_SYNC_CHUNK_LOAD, startedAt);
        }
    }

    /** 为可能跨区块生成结构的树木等对象补齐周边区块。 */
    public static void ensurePositionNeighborhoodLoaded(ServerLevel level, BlockPos pos, int radius) {
        int safeRadius = Math.max(0, radius);
        ensureBoundsLoaded(
            level,
            pos.offset(-safeRadius, 0, -safeRadius),
            pos.offset(safeRadius, 0, safeRadius)
        );
    }

    /** 为畜棚、鸡舍等小型结算区域加载其覆盖的区块。 */
    public static void ensureBoundsLoaded(ServerLevel level, BlockPos min, BlockPos max) {
        int minChunkX = Math.min(min.getX(), max.getX()) >> 4;
        int maxChunkX = Math.max(min.getX(), max.getX()) >> 4;
        int minChunkZ = Math.min(min.getZ(), max.getZ()) >> 4;
        int maxChunkZ = Math.max(min.getZ(), max.getZ()) >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                ensurePositionLoaded(level, new BlockPos(chunkX << 4, min.getY(), chunkZ << 4));
            }
        }
    }

    /**
     * 获取当前所有有在线成员的农场 owner UUID 集合。
     */
    public static Set<UUID> getOnlineFarmOwners(ServerLevel level) {
        Set<UUID> owners = new HashSet<>();
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            UUID ownerUUID = FarmInstanceRegistry.get().getOwnerForPlayer(player.getUUID());
            if (ownerUUID != null) {
                owners.add(ownerUUID);
            }
        }
        return owners;
    }

    /**
     * 获取某个玩家的农场实例（owner 或 member 均可）。
     */
    @Nullable
    public static FarmInstance getPlayerFarm(UUID playerUUID) {
        return FarmInstanceRegistry.get().getFarmForPlayer(playerUUID);
    }

    // ── 跨季宽限期倒计时 ──

    /**
     * 每日结算开始时调用。对所有有在线成员的农场递减宽限天数，
     * 并向在线成员发送提示消息。
     */
    private static void tickGracePeriods(ServerLevel level) {
        FarmInstanceRegistry registry = FarmInstanceRegistry.get();
        boolean dirty = false;

        for (FarmInstance farm : registry.getAllFarms()) {
            int remaining = farm.getGraceDaysLeft();
            if (remaining <= 0) continue;

            // 检查是否有任一成员在线
            boolean anyOnline = false;
            for (UUID farmer : farm.getAllFarmers()) {
                if (cachedOnlinePlayers != null ? cachedOnlinePlayers.contains(farmer)
                        : level.getServer().getPlayerList().getPlayer(farmer) != null) {
                    anyOnline = true;
                    break;
                }
            }
            if (!anyOnline) continue;

            remaining--;
            farm.setGraceDaysLeft(remaining);
            dirty = true;

            // 向所有在线成员发送提示
            for (UUID farmer : farm.getAllFarmers()) {
                ServerPlayer p = level.getServer().getPlayerList().getPlayer(farmer);
                if (p == null) continue;
                if (remaining > 0) {
                    com.stardew.craft.network.GlobalHudMessagePayload.sendTo(
                            p,
                            net.minecraft.network.chat.Component.translatable(
                                    "stardewcraft.farm.grace_period.remaining", remaining));
                } else {
                    com.stardew.craft.network.GlobalHudMessagePayload.sendTo(
                            p,
                            net.minecraft.network.chat.Component.translatable(
                                    "stardewcraft.farm.grace_period.expired"));
                }
            }
        }

        if (dirty) registry.setDirty();
    }
}
