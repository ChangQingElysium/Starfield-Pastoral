package com.stardew.craft.animal.service;

import net.minecraft.core.BlockPos;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.LongPredicate;

/**
 * Section-based index used by animal grass targeting.
 *
 * <p>The index deliberately contains no world access. A section is populated once by the runtime
 * service and then maintained by pasture-grass block changes, so individual animals only iterate
 * known grass positions instead of scanning a three-dimensional block volume.
 */
final class AnimalGrassSpatialIndex {
    private final Map<SectionKey, Set<Long>> grassBySection = new HashMap<>();

    boolean isIndexed(int sectionX, int sectionY, int sectionZ) {
        return grassBySection.containsKey(
                new SectionKey(sectionX, sectionY, sectionZ));
    }

    void replaceSection(
            int sectionX,
            int sectionY,
            int sectionZ,
            Collection<BlockPos> grass
    ) {
        Set<Long> positions = new HashSet<>();
        for (BlockPos pos : grass) {
            positions.add(pos.asLong());
        }
        grassBySection.put(
                new SectionKey(sectionX, sectionY, sectionZ),
                positions
        );
    }

    void update(BlockPos pos, boolean present) {
        SectionKey key = SectionKey.at(pos);
        Set<Long> positions = grassBySection.get(key);
        if (positions == null) {
            return;
        }
        if (present) {
            positions.add(pos.asLong());
        } else {
            positions.remove(pos.asLong());
        }
    }

    void invalidateChunk(int chunkX, int chunkZ) {
        grassBySection.keySet().removeIf(
                key -> key.x == chunkX && key.z == chunkZ);
    }

    @Nullable
    BlockPos findNearest(
            BlockPos origin,
            int radius,
            int verticalRadius,
            LongPredicate accepted
    ) {
        int minSectionX = Math.floorDiv(
                origin.getX() - radius, 16);
        int maxSectionX = Math.floorDiv(
                origin.getX() + radius, 16);
        int minSectionY = Math.floorDiv(
                origin.getY() - verticalRadius, 16);
        int maxSectionY = Math.floorDiv(
                origin.getY() + verticalRadius, 16);
        int minSectionZ = Math.floorDiv(
                origin.getZ() - radius, 16);
        int maxSectionZ = Math.floorDiv(
                origin.getZ() + radius, 16);

        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int sectionX = minSectionX;
             sectionX <= maxSectionX;
             sectionX++) {
            for (int sectionY = minSectionY;
                 sectionY <= maxSectionY;
                 sectionY++) {
                for (int sectionZ = minSectionZ;
                     sectionZ <= maxSectionZ;
                     sectionZ++) {
                    Set<Long> positions = grassBySection.get(
                            new SectionKey(
                                    sectionX,
                                    sectionY,
                                    sectionZ));
                    if (positions == null || positions.isEmpty()) {
                        continue;
                    }
                    for (long packed : positions) {
                        BlockPos candidate = BlockPos.of(packed);
                        if (Math.abs(candidate.getX() - origin.getX())
                                > radius
                                || Math.abs(candidate.getY()
                                - origin.getY()) > verticalRadius
                                || Math.abs(candidate.getZ()
                                - origin.getZ()) > radius
                                || !accepted.test(packed)) {
                            continue;
                        }
                        double distance =
                                candidate.distSqr(origin);
                        if (distance < bestDistance) {
                            best = candidate;
                            bestDistance = distance;
                        }
                    }
                }
            }
        }
        return best == null ? null : best.immutable();
    }

    private record SectionKey(int x, int y, int z) {
        private static SectionKey at(BlockPos pos) {
            return new SectionKey(
                    Math.floorDiv(pos.getX(), 16),
                    Math.floorDiv(pos.getY(), 16),
                    Math.floorDiv(pos.getZ(), 16)
            );
        }
    }
}
