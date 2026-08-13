package com.stardew.craft.npc.runtime;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Manages forced chunk loading for NPC route targets and corridors.
 * Extracted from NpcCentralMovementService.
 */
@SuppressWarnings("null")
public final class NpcChunkForceManager {

    private static final int MAX_FORCED_CORRIDOR_CHUNK_DELTA = 16;

    private static final Map<ServerLevel, LevelChunkState> LEVEL_STATES = new IdentityHashMap<>();

    private NpcChunkForceManager() {
    }

    /** Clear all state (call on server context change). */
    public static void resetState() {
        LEVEL_STATES.clear();
    }

    /** Release ALL forced chunks (call when no player is in dimension). */
    public static void releaseAllForcedChunks(ServerLevel level) {
        if (level == null) return;
        LevelChunkState state = LEVEL_STATES.remove(level);
        if (state == null) return;
        for (Long key : state.forcedTargetChunkByNpc.values()) {
            level.setChunkForced((int)(key >> 32), (int)(long)key, false);
        }
        for (Set<Long> chunks : state.forcedRouteChunksByNpc.values()) {
            for (Long key : chunks) {
                level.setChunkForced((int)(key >> 32), (int)(long)key, false);
            }
        }
    }

    public static void ensureRouteTargetChunkForced(ServerLevel level, String rawNpcId, Vec3 target) {
        if (level == null || target == null || rawNpcId == null || rawNpcId.isBlank()) {
            return;
        }
        String npcId = rawNpcId.toLowerCase(Locale.ROOT);
        int chunkX = ((int) Math.floor(target.x)) >> 4;
        int chunkZ = ((int) Math.floor(target.z)) >> 4;
        long newKey = (((long) chunkX) << 32) | (((long) chunkZ) & 0xFFFFFFFFL);
        LevelChunkState state = state(level);

        Long oldKey = state.forcedTargetChunkByNpc.get(npcId);
        if (oldKey != null && oldKey == newKey) {
            return;
        }

        state.forcedTargetChunkByNpc.put(npcId, newKey);
        level.setChunkForced(chunkX, chunkZ, true);
        if (oldKey != null) {
            releaseChunkIfUnused(level, state, oldKey);
        }
    }

    public static String currentForcedTargetChunk(ServerLevel level, String rawNpcId) {
        if (level == null || rawNpcId == null || rawNpcId.isBlank()) {
            return "<none>";
        }
        LevelChunkState state = LEVEL_STATES.get(level);
        Long key = state == null ? null : state.forcedTargetChunkByNpc.get(rawNpcId.toLowerCase(Locale.ROOT));
        if (key == null) {
            return "<none>";
        }
        int chunkX = (int) (key >> 32);
        int chunkZ = (int) (long) key;
        return chunkX + "," + chunkZ;
    }

    public static void ensureRouteCorridorChunksForced(ServerLevel level, String rawNpcId, Vec3 from, Vec3 to) {
        if (level == null || rawNpcId == null || rawNpcId.isBlank() || from == null || to == null) {
            return;
        }

        String npcId = rawNpcId.toLowerCase(Locale.ROOT);
        int startX = ((int) Math.floor(from.x)) >> 4;
        int startZ = ((int) Math.floor(from.z)) >> 4;
        int endX = ((int) Math.floor(to.x)) >> 4;
        int endZ = ((int) Math.floor(to.z)) >> 4;

        // 快速跳过：NPC 仍在同一 chunk 端点对中
        long compositeKey = (((long)(startX & 0xFFFF)) << 48) | (((long)(startZ & 0xFFFF)) << 32)
                          | (((long)(endX & 0xFFFF)) << 16) | ((long)(endZ & 0xFFFF));
        LevelChunkState state = state(level);
        Long cachedKey = state.corridorEndpointCache.get(npcId);
        if (cachedKey != null && cachedKey == compositeKey) {
            return;
        }

        if (Math.abs(endX - startX) > MAX_FORCED_CORRIDOR_CHUNK_DELTA
            || Math.abs(endZ - startZ) > MAX_FORCED_CORRIDOR_CHUNK_DELTA) {
            Set<Long> prev = state.forcedRouteChunksByNpc.remove(npcId);
            state.corridorEndpointCache.remove(npcId);
            if (prev != null) {
                for (Long oldKey : prev) {
                    releaseChunkIfUnused(level, state, oldKey);
                }
            }
            return;
        }

        Set<Long> next = chunkLine(startX, startZ, endX, endZ);
        Set<Long> prev = state.forcedRouteChunksByNpc.getOrDefault(npcId, Set.of());

        state.forcedRouteChunksByNpc.put(npcId, next);
        state.corridorEndpointCache.put(npcId, compositeKey);
        for (Long key : next) {
            int chunkX = (int) (key >> 32);
            int chunkZ = (int) (long) key;
            level.setChunkForced(chunkX, chunkZ, true);
        }
        for (Long oldKey : prev) {
            if (!next.contains(oldKey)) {
                releaseChunkIfUnused(level, state, oldKey);
            }
        }
    }

    public static void releaseInactiveForcedChunks(ServerLevel level, Set<String> activeNpcIds) {
        if (level == null) {
            return;
        }
        LevelChunkState state = LEVEL_STATES.get(level);
        if (state == null) {
            return;
        }

        List<String> staleTargets = new java.util.ArrayList<>();
        for (String npcId : state.forcedTargetChunkByNpc.keySet()) {
            if (!activeNpcIds.contains(npcId)) {
                staleTargets.add(npcId);
            }
        }
        for (String npcId : staleTargets) {
            Long key = state.forcedTargetChunkByNpc.remove(npcId);
            if (key == null) {
                continue;
            }
            releaseChunkIfUnused(level, state, key);
        }

        List<String> staleCorridors = new java.util.ArrayList<>();
        for (String npcId : state.forcedRouteChunksByNpc.keySet()) {
            if (!activeNpcIds.contains(npcId)) {
                staleCorridors.add(npcId);
            }
        }
        for (String npcId : staleCorridors) {
            Set<Long> chunks = state.forcedRouteChunksByNpc.remove(npcId);
            state.corridorEndpointCache.remove(npcId);
            if (chunks == null) {
                continue;
            }
            for (Long key : chunks) {
                releaseChunkIfUnused(level, state, key);
            }
        }
    }

    public static void releaseNpcForcedChunks(ServerLevel level, String rawNpcId) {
        if (level == null || rawNpcId == null || rawNpcId.isBlank()) {
            return;
        }
        String npcId = rawNpcId.toLowerCase(Locale.ROOT);
        LevelChunkState state = LEVEL_STATES.get(level);
        if (state == null) {
            return;
        }
        Long target = state.forcedTargetChunkByNpc.remove(npcId);
        Set<Long> corridor = state.forcedRouteChunksByNpc.remove(npcId);
        state.corridorEndpointCache.remove(npcId);
        if (target != null) {
            releaseChunkIfUnused(level, state, target);
        }
        if (corridor != null) {
            for (Long key : corridor) {
                releaseChunkIfUnused(level, state, key);
            }
        }
    }

    private static void releaseChunkIfUnused(ServerLevel level, LevelChunkState state, long key) {
        if (state.forcedTargetChunkByNpc.containsValue(key)) {
            return;
        }
        for (Set<Long> chunks : state.forcedRouteChunksByNpc.values()) {
            if (chunks.contains(key)) {
                return;
            }
        }
        level.setChunkForced((int) (key >> 32), (int) key, false);
    }

    private static LevelChunkState state(ServerLevel level) {
        return LEVEL_STATES.computeIfAbsent(level, ignored -> new LevelChunkState());
    }

    private static final class LevelChunkState {
        private final Map<String, Long> forcedTargetChunkByNpc = new HashMap<>();
        private final Map<String, Set<Long>> forcedRouteChunksByNpc = new HashMap<>();
        private final Map<String, Long> corridorEndpointCache = new HashMap<>();
    }

    private static Set<Long> chunkLine(int x0, int z0, int x1, int z1) {
        Set<Long> out = new LinkedHashSet<>();

        int dx = Math.abs(x1 - x0);
        int dz = Math.abs(z1 - z0);
        int sx = x0 < x1 ? 1 : -1;
        int sz = z0 < z1 ? 1 : -1;
        int err = dx - dz;

        int x = x0;
        int z = z0;
        while (true) {
            out.add(packChunk(x, z));
            if (x == x1 && z == z1) {
                break;
            }
            int e2 = 2 * err;
            if (e2 > -dz) {
                err -= dz;
                x += sx;
            }
            if (e2 < dx) {
                err += dx;
                z += sz;
            }
        }

        return out;
    }

    private static long packChunk(int chunkX, int chunkZ) {
        return (((long) chunkX) << 32) | (((long) chunkZ) & 0xFFFFFFFFL);
    }
}
