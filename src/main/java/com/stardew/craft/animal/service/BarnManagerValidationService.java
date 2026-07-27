package com.stardew.craft.animal.service;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@SuppressWarnings("null")
public final class BarnManagerValidationService {
    private BarnManagerValidationService() {
    }

    public static ValidationResult validateForTier(ServerLevel level, BlockPos managerPos, int targetTier) {
        TierRequirement requirement = TierRequirement.fromTier(targetTier);
        ScanResult scan = scan(level, managerPos, requirement.minInteriorBlocks());
        AnimalBuildingValidationRules.ValidationOutcome outcome =
                AnimalBuildingValidationRules.evaluate(
                        "barn",
                        requirement.shared(),
                        scan.sharedFacts());
        return new ValidationResult(
                outcome.success(),
                targetTier,
                requirement,
                scan,
                outcome.message());
    }

    private static ScanResult scan(ServerLevel level, BlockPos managerPos, int minInteriorBlocks) {
        AnimalBuildingValidationRules.Bounds bounds =
                AnimalBuildingValidationRules.boundsFor(
                        managerPos, "barn", minInteriorBlocks);
        int scanMinX = bounds.minX();
        int scanMaxX = bounds.maxX();
        int scanMinY = bounds.minY();
        int scanMaxY = bounds.maxY();
        int scanMinZ = bounds.minZ();
        int scanMaxZ = bounds.maxZ();

        List<BlockPos> startCandidates =
                AnimalBuildingValidationRules.collectStartCandidates(
                        level, managerPos, bounds);

        if (startCandidates.isEmpty()) {
            return new ScanResult(
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                true,
                0,
                scanMinX,
                scanMinY,
                scanMinZ,
                scanMaxX,
                scanMaxY,
                scanMaxZ,
                Collections.emptySet(),
                Collections.emptySet()
            );
        }

        FloodResult flood = null;
        for (BlockPos start : startCandidates) {
            FloodResult current = floodInterior(level, List.of(start), scanMinX, scanMaxX, scanMinY, scanMaxY, scanMinZ, scanMaxZ);
            if (flood == null || isBetterComponent(current, flood)) {
                flood = current;
            }
        }

        if (flood == null) {
            return new ScanResult(
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                false,
                0,
                managerPos.getX(),
                managerPos.getY(),
                managerPos.getZ(),
                managerPos.getX(),
                managerPos.getY(),
                managerPos.getZ(),
                Collections.emptySet(),
                Collections.emptySet()
            );
        }

        Set<Long> scopedInterior = flood.enclosed ? flood.interiorAirCells : Collections.emptySet();
        AnimalBuildingValidationRules.FacilityCounts facilities =
                AnimalBuildingValidationRules.countInteriorFacilities(
                        level, scopedInterior, bounds);

        int interiorAirCount = flood.enclosed ? flood.airCount : 0;
        int width = flood.enclosed && flood.maxX >= flood.minX ? flood.maxX - flood.minX + 1 : 0;
        int length = flood.enclosed && flood.maxZ >= flood.minZ ? flood.maxZ - flood.minZ + 1 : 0;
        int height = flood.enclosed && flood.maxY >= flood.minY ? flood.maxY - flood.minY + 1 : 0;

        return new ScanResult(
            facilities.feedTroughCount(),
            facilities.autoFeedTroughCount(),
            facilities.hayHopperCount(),
            facilities.incubatorCount(),
            interiorAirCount,
            width,
            length,
            height,
            flood.enclosed,
            flood.doorCount,
            flood.minX,
            flood.minY,
            flood.minZ,
            flood.maxX,
            flood.maxY,
            flood.maxZ,
            Collections.unmodifiableSet(new LinkedHashSet<>(scopedInterior)),
            Collections.unmodifiableSet(flood.boundaryDoorCells)
        );
    }

    private static boolean isBetterComponent(FloodResult current, FloodResult best) {
        if (current.enclosed != best.enclosed) {
            return current.enclosed;
        }
        return current.airCount > best.airCount;
    }

    private static FloodResult floodInterior(ServerLevel level,
                                             List<BlockPos> starts,
                                             int scanMinX,
                                             int scanMaxX,
                                             int scanMinY,
                                             int scanMaxY,
                                             int scanMinZ,
                                             int scanMaxZ) {
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();
        Set<Long> interiorAirCells = new LinkedHashSet<>();
        Set<Long> boundaryDoorCells = new LinkedHashSet<>();

        for (BlockPos start : starts) {
            long key = pack(start);
            if (visited.add(key)) {
                queue.add(start);
            }
        }

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        boolean enclosed = true;
        while (!queue.isEmpty()) {
            BlockPos pos = queue.removeFirst();
            if (!withinScan(pos, scanMinX, scanMaxX, scanMinY, scanMaxY, scanMinZ, scanMaxZ)) {
                enclosed = false;
                continue;
            }

            BlockState state = level.getBlockState(pos);
            if (!state.isAir()) {
                continue;
            }

            long packed = pack(pos);
            interiorAirCells.add(packed);

            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());

            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (!withinScan(next, scanMinX, scanMaxX, scanMinY, scanMaxY, scanMinZ, scanMaxZ)) {
                    enclosed = false;
                    continue;
                }

                BlockState nextState = level.getBlockState(next);
                if (nextState.isAir()) {
                    long nextKey = pack(next);
                    if (visited.add(nextKey)) {
                        queue.add(next);
                    }
                    continue;
                }

                if (isDoorLike(nextState)) {
                    long doorKey = pack(next);
                    boundaryDoorCells.add(doorKey);
                }
            }
        }

        if (interiorAirCells.isEmpty()) {
            minX = scanMinX;
            minY = scanMinY;
            minZ = scanMinZ;
            maxX = scanMaxX;
            maxY = scanMaxY;
            maxZ = scanMaxZ;
        }

        Set<Long> exteriorDoors =
                AnimalBuildingValidationRules.exteriorDoorCells(
                        level,
                        interiorAirCells,
                        boundaryDoorCells,
                        new AnimalBuildingValidationRules.Bounds(
                                scanMinX,
                                scanMaxX,
                                scanMinY,
                                scanMaxY,
                                scanMinZ,
                                scanMaxZ
                        )
                );
        return new FloodResult(
                interiorAirCells.size(),
                enclosed,
                exteriorDoors.size(),
                minX,
                minY,
                minZ,
                maxX,
                maxY,
                maxZ,
                interiorAirCells,
                exteriorDoors
        );
    }

    private static boolean isDoorLike(BlockState state) {
        return state.is(BlockTags.DOORS) || state.is(BlockTags.FENCE_GATES);
    }

    private static boolean withinScan(BlockPos pos,
                                      int scanMinX,
                                      int scanMaxX,
                                      int scanMinY,
                                      int scanMaxY,
                                      int scanMinZ,
                                      int scanMaxZ) {
        return pos.getX() >= scanMinX && pos.getX() <= scanMaxX
            && pos.getY() >= scanMinY && pos.getY() <= scanMaxY
            && pos.getZ() >= scanMinZ && pos.getZ() <= scanMaxZ;
    }

    private static long pack(BlockPos pos) {
        return BlockPos.asLong(pos.getX(), pos.getY(), pos.getZ());
    }

    private record FloodResult(int airCount,
                               boolean enclosed,
                               int doorCount,
                               int minX,
                               int minY,
                               int minZ,
                               int maxX,
                               int maxY,
                               int maxZ,
                               Set<Long> interiorAirCells,
                               Set<Long> boundaryDoorCells) {
    }

    public record ValidationResult(boolean success,
                                   int targetTier,
                                   TierRequirement requirement,
                                   ScanResult scan,
                                   Component message) {
    }

    public record ScanResult(int feedTroughCount,
                             int autoFeedTroughCount,
                             int hayHopperCount,
                             int incubatorCount,
                             int interiorAirCount,
                             int width,
                             int length,
                             int height,
                             boolean enclosed,
                             int doorCount,
                             int interiorMinX,
                             int interiorMinY,
                             int interiorMinZ,
                             int interiorMaxX,
                             int interiorMaxY,
                             int interiorMaxZ,
                             Set<Long> interiorAirCells,
                             Set<Long> boundaryDoorCells) {
        public boolean hasInteriorSpace() {
            return width > 0 && length > 0 && height > 0;
        }

        private AnimalBuildingValidationRules.ScanFacts sharedFacts() {
            return new AnimalBuildingValidationRules.ScanFacts(
                    feedTroughCount,
                    autoFeedTroughCount,
                    hayHopperCount,
                    incubatorCount,
                    interiorAirCount,
                    width,
                    length,
                    height,
                    enclosed,
                    doorCount
            );
        }
    }

    public record TierRequirement(int feedTroughCount,
                                  int autoFeedTroughCount,
                                  int hayHopperCount,
                                  int incubatorCount,
                                  int minInteriorBlocks,
                                  boolean requireEnclosed,
                                  boolean requireDoor,
                                  int minDoorCount) {
        public static TierRequirement fromTier(int tier) {
            AnimalBuildingValidationRules.Requirements shared =
                    AnimalBuildingValidationRules.requirementsFor(
                            "barn", tier);
            return new TierRequirement(
                    shared.feedTroughCount(),
                    shared.autoFeedTroughCount(),
                    shared.hayHopperCount(),
                    shared.incubatorCount(),
                    shared.minInteriorBlocks(),
                    shared.requireEnclosed(),
                    shared.requireDoor(),
                    shared.minDoorCount()
            );
        }

        private AnimalBuildingValidationRules.Requirements shared() {
            return new AnimalBuildingValidationRules.Requirements(
                    feedTroughCount,
                    autoFeedTroughCount,
                    hayHopperCount,
                    incubatorCount,
                    minInteriorBlocks,
                    requireEnclosed,
                    requireDoor,
                    minDoorCount
            );
        }
    }
}
