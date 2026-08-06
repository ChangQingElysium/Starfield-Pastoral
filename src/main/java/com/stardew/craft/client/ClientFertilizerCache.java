package com.stardew.craft.client;

import com.stardew.craft.block.FertilizerType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/** Client-side fertilizer state, spatially indexed by dimension and chunk. */
public final class ClientFertilizerCache {
    private static final Map<ResourceKey<Level>, Map<Long, Map<BlockPos, FertilizerType>>>
            DIMENSION_CHUNKS = new HashMap<>();

    private ClientFertilizerCache() {
    }

    public static void setFertilizer(BlockPos pos, FertilizerType type) {
        ResourceKey<Level> dimension = currentDimension();
        if (dimension != null) {
            setFertilizer(dimension, pos, type);
        }
    }

    public static void setFertilizer(Level level, BlockPos pos, FertilizerType type) {
        setFertilizer(level.dimension(), pos, type);
    }

    public static void setFertilizer(
            ResourceKey<Level> dimension,
            BlockPos pos,
            FertilizerType type
    ) {
        chunks(dimension, true)
                .computeIfAbsent(ChunkPos.asLong(pos), ignored -> new HashMap<>())
                .put(pos.immutable(), type);
    }

    @Nullable
    public static FertilizerType getFertilizer(BlockPos pos) {
        ResourceKey<Level> dimension = currentDimension();
        return dimension == null ? null : getFertilizer(dimension, pos);
    }

    @Nullable
    public static FertilizerType getFertilizer(Level level, BlockPos pos) {
        return getFertilizer(level.dimension(), pos);
    }

    @Nullable
    public static FertilizerType getFertilizer(ResourceKey<Level> dimension, BlockPos pos) {
        Map<Long, Map<BlockPos, FertilizerType>> chunks = chunks(dimension, false);
        if (chunks == null) {
            return null;
        }
        Map<BlockPos, FertilizerType> chunk = chunks.get(ChunkPos.asLong(pos));
        return chunk == null ? null : chunk.get(pos);
    }

    public static void removeFertilizer(BlockPos pos) {
        ResourceKey<Level> dimension = currentDimension();
        if (dimension != null) {
            removeFertilizer(dimension, pos);
        }
    }

    public static void removeFertilizer(Level level, BlockPos pos) {
        removeFertilizer(level.dimension(), pos);
    }

    public static void removeFertilizer(ResourceKey<Level> dimension, BlockPos pos) {
        Map<Long, Map<BlockPos, FertilizerType>> chunks = chunks(dimension, false);
        if (chunks == null) {
            return;
        }
        long chunkKey = ChunkPos.asLong(pos);
        Map<BlockPos, FertilizerType> chunk = chunks.get(chunkKey);
        if (chunk == null) {
            return;
        }
        chunk.remove(pos);
        if (chunk.isEmpty()) {
            chunks.remove(chunkKey);
            removeDimensionIfEmpty(dimension, chunks);
        }
    }

    /** Atomically replaces all fertilizer state for one server-synchronized chunk. */
    public static void replaceChunk(
            ResourceKey<Level> dimension,
            ChunkPos chunkPos,
            Map<BlockPos, FertilizerType> snapshot
    ) {
        Map<Long, Map<BlockPos, FertilizerType>> chunks = chunks(dimension, true);
        long chunkKey = chunkPos.toLong();
        if (snapshot.isEmpty()) {
            chunks.remove(chunkKey);
            removeDimensionIfEmpty(dimension, chunks);
            return;
        }

        Map<BlockPos, FertilizerType> replacement = new HashMap<>();
        for (Map.Entry<BlockPos, FertilizerType> entry : snapshot.entrySet()) {
            BlockPos pos = entry.getKey();
            if (ChunkPos.asLong(pos) == chunkKey) {
                replacement.put(pos.immutable(), entry.getValue());
            }
        }
        if (replacement.isEmpty()) {
            chunks.remove(chunkKey);
            removeDimensionIfEmpty(dimension, chunks);
        } else {
            chunks.put(chunkKey, replacement);
        }
    }

    public static void clearChunk(ChunkPos chunkPos) {
        ResourceKey<Level> dimension = currentDimension();
        if (dimension != null) {
            clearChunk(dimension, chunkPos);
        }
    }

    public static void clearChunk(ResourceKey<Level> dimension, ChunkPos chunkPos) {
        Map<Long, Map<BlockPos, FertilizerType>> chunks = chunks(dimension, false);
        if (chunks != null) {
            chunks.remove(chunkPos.toLong());
            removeDimensionIfEmpty(dimension, chunks);
        }
    }

    public static void clear() {
        DIMENSION_CHUNKS.clear();
    }

    public static boolean hasFertilizer(BlockPos pos) {
        return getFertilizer(pos) != null;
    }

    /** Iterates only indexed chunks inside the current view square and allocates no snapshot map. */
    public static void forEachInChunkRange(
            ResourceKey<Level> dimension,
            ChunkPos center,
            int radius,
            BiConsumer<BlockPos, FertilizerType> consumer
    ) {
        forEachInChunkRange(dimension, center, radius, (chunkX, chunkZ) -> true, consumer);
    }

    public static void forEachInChunkRange(
            ResourceKey<Level> dimension,
            ChunkPos center,
            int radius,
            ChunkFilter chunkFilter,
            BiConsumer<BlockPos, FertilizerType> consumer
    ) {
        Map<Long, Map<BlockPos, FertilizerType>> chunks = chunks(dimension, false);
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        int safeRadius = Math.max(0, radius);
        long side = (long) safeRadius * 2L + 1L;
        if (chunks.size() <= side * side) {
            for (Map.Entry<Long, Map<BlockPos, FertilizerType>> indexedChunk
                    : chunks.entrySet()) {
                int chunkX = ChunkPos.getX(indexedChunk.getKey());
                int chunkZ = ChunkPos.getZ(indexedChunk.getKey());
                if (Math.abs((long) chunkX - center.x) <= safeRadius
                        && Math.abs((long) chunkZ - center.z) <= safeRadius
                        && chunkFilter.test(chunkX, chunkZ)) {
                    indexedChunk.getValue().forEach(consumer);
                }
            }
            return;
        }
        for (int chunkX = center.x - safeRadius; chunkX <= center.x + safeRadius; chunkX++) {
            for (int chunkZ = center.z - safeRadius; chunkZ <= center.z + safeRadius; chunkZ++) {
                Map<BlockPos, FertilizerType> chunk = chunks.get(ChunkPos.asLong(chunkX, chunkZ));
                if (chunk != null && chunkFilter.test(chunkX, chunkZ)) {
                    chunk.forEach(consumer);
                }
            }
        }
    }

    @FunctionalInterface
    public interface ChunkFilter {
        boolean test(int chunkX, int chunkZ);
    }

    /** Snapshot retained for diagnostics and integrations; the world renderer does not call it. */
    public static Map<BlockPos, FertilizerType> snapshot() {
        ResourceKey<Level> dimension = currentDimension();
        return dimension == null ? Map.of() : snapshot(dimension);
    }

    public static Map<BlockPos, FertilizerType> snapshot(Level level) {
        return snapshot(level.dimension());
    }

    public static Map<BlockPos, FertilizerType> snapshot(ResourceKey<Level> dimension) {
        Map<Long, Map<BlockPos, FertilizerType>> chunks = chunks(dimension, false);
        if (chunks == null || chunks.isEmpty()) {
            return Map.of();
        }
        Map<BlockPos, FertilizerType> snapshot = new HashMap<>();
        chunks.values().forEach(snapshot::putAll);
        return Map.copyOf(snapshot);
    }

    @Nullable
    private static Map<Long, Map<BlockPos, FertilizerType>> chunks(
            ResourceKey<Level> dimension,
            boolean create
    ) {
        return create
                ? DIMENSION_CHUNKS.computeIfAbsent(dimension, ignored -> new HashMap<>())
                : DIMENSION_CHUNKS.get(dimension);
    }

    private static void removeDimensionIfEmpty(
            ResourceKey<Level> dimension,
            Map<Long, Map<BlockPos, FertilizerType>> chunks
    ) {
        if (chunks.isEmpty()) {
            DIMENSION_CHUNKS.remove(dimension);
        }
    }

    @Nullable
    private static ResourceKey<Level> currentDimension() {
        Level level = Minecraft.getInstance().level;
        return level == null ? null : level.dimension();
    }
}
