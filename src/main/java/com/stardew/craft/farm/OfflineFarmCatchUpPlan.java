package com.stardew.craft.farm;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

record OfflineFarmCatchUpPlan(
        List<GlobalPos> crops,
        List<GlobalPos> trees,
        List<GlobalPos> sprinklers,
        Set<ChunkPos> requiredChunks) {

    OfflineFarmCatchUpPlan {
        crops = List.copyOf(Objects.requireNonNull(crops, "crops"));
        trees = List.copyOf(Objects.requireNonNull(trees, "trees"));
        sprinklers = List.copyOf(Objects.requireNonNull(sprinklers, "sprinklers"));
        requiredChunks = Set.copyOf(Objects.requireNonNull(requiredChunks, "requiredChunks"));
    }

    static OfflineFarmCatchUpPlan create(
            ResourceKey<Level> dimension,
            BlockPos farmMin,
            BlockPos farmMax,
            Collection<GlobalPos> cropPositions,
            Collection<GlobalPos> treePositions,
            Collection<GlobalPos> sprinklerPositions) {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(farmMin, "farmMin");
        Objects.requireNonNull(farmMax, "farmMax");
        Objects.requireNonNull(cropPositions, "cropPositions");
        Objects.requireNonNull(treePositions, "treePositions");
        Objects.requireNonNull(sprinklerPositions, "sprinklerPositions");

        int minX = Math.min(farmMin.getX(), farmMax.getX());
        int maxX = Math.max(farmMin.getX(), farmMax.getX());
        int minZ = Math.min(farmMin.getZ(), farmMax.getZ());
        int maxZ = Math.max(farmMin.getZ(), farmMax.getZ());

        List<GlobalPos> crops = retainInSourceOrder(dimension, minX, maxX, minZ, maxZ, cropPositions);
        List<GlobalPos> trees = retainInSourceOrder(dimension, minX, maxX, minZ, maxZ, treePositions);
        List<GlobalPos> sprinklers = retainInSourceOrder(dimension, minX, maxX, minZ, maxZ, sprinklerPositions);

        Set<ChunkPos> requiredChunks = new HashSet<>();
        crops.forEach(position -> addIntersectingChunks(requiredChunks, position.pos(), 0));
        trees.forEach(position -> addIntersectingChunks(requiredChunks, position.pos(), 8));
        sprinklers.forEach(position -> addIntersectingChunks(requiredChunks, position.pos(), 2));

        return new OfflineFarmCatchUpPlan(crops, trees, sprinklers, requiredChunks);
    }

    private static List<GlobalPos> retainInSourceOrder(
            ResourceKey<Level> dimension,
            int minX,
            int maxX,
            int minZ,
            int maxZ,
            Collection<GlobalPos> positions) {
        List<GlobalPos> retained = new ArrayList<>();
        for (GlobalPos position : positions) {
            Objects.requireNonNull(position, "positions contains null");
            BlockPos blockPos = position.pos();
            if (dimension.equals(position.dimension())
                    && blockPos.getX() >= minX && blockPos.getX() <= maxX
                    && blockPos.getZ() >= minZ && blockPos.getZ() <= maxZ) {
                retained.add(position);
            }
        }
        return retained;
    }

    private static void addIntersectingChunks(Set<ChunkPos> chunks, BlockPos center, int radius) {
        int minChunkX = SectionPos.blockToSectionCoord(center.getX() - radius);
        int maxChunkX = SectionPos.blockToSectionCoord(center.getX() + radius);
        int minChunkZ = SectionPos.blockToSectionCoord(center.getZ() - radius);
        int maxChunkZ = SectionPos.blockToSectionCoord(center.getZ() + radius);

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                chunks.add(new ChunkPos(chunkX, chunkZ));
            }
        }
    }
}
