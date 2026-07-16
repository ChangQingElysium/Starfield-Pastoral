package com.stardew.craft.animal.service;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Tracks entity chunks sanitized during the current server session.
 * Animals in these chunks temporarily skip vanilla collision pushing so a
 * previously polluted entity section cannot stall the server while it heals.
 */
public final class AnimalEntityRecoveryState {
    private static final Map<ServerLevel, Set<Long>> RECOVERING_CHUNKS =
        Collections.synchronizedMap(new WeakHashMap<>());

    private AnimalEntityRecoveryState() {
    }

    public static void markRecovering(ServerLevel level, ChunkPos pos) {
        synchronized (RECOVERING_CHUNKS) {
            RECOVERING_CHUNKS.computeIfAbsent(level, ignored -> new java.util.HashSet<>()).add(pos.toLong());
        }
    }

    public static boolean isRecovering(ServerLevel level, ChunkPos pos) {
        synchronized (RECOVERING_CHUNKS) {
            Set<Long> chunks = RECOVERING_CHUNKS.get(level);
            return chunks != null && chunks.contains(pos.toLong());
        }
    }

    public static void clear(ServerLevel level) {
        RECOVERING_CHUNKS.remove(level);
    }
}
