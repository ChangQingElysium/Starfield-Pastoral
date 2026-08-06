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
public final class CoopManagerValidationService {
    private CoopManagerValidationService() {
    }

    public static ValidationResult validateForTier(ServerLevel level, BlockPos managerPos, int targetTier) {
        TierRequirement requirement = TierRequirement.fromTier(targetTier);
        ScanResult scan = scan(level, managerPos, targetTier, requirement.minInteriorBlocks());
        AnimalBuildingValidationRules.ValidationOutcome outcome =
                AnimalBuildingValidationRules.evaluate(
                        "coop",
                        requirement.shared(),
                        scan.sharedFacts());
        return new ValidationResult(
                outcome.success(),
                targetTier,
                requirement,
                scan,
                outcome.message());
    }

    private static ScanResult scan(ServerLevel level, BlockPos managerPos, int tier, int minInteriorBlocks) {
        AnimalBuildingValidationRules.Bounds bounds =
                AnimalBuildingValidationRules.boundsFor(
                        managerPos, "coop", tier, minInteriorBlocks);
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

        ScanResult best = null;
        for (BlockPos start : startCandidates) {
            ScanResult current = scanAirComponent(
                level,
                start,
                scanMinX,
                scanMaxX,
                scanMinY,
                scanMaxY,
                scanMinZ,
                scanMaxZ
            );
            if (best == null || isBetterComponent(current, best)) {
                best = current;
            }
        }

        if (best == null) {
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

        Set<Long> scopedInterior = best.enclosed() ? best.interiorAirCells() : Collections.emptySet();
        AnimalBuildingValidationRules.FacilityCounts facilities =
                AnimalBuildingValidationRules.countInteriorFacilities(
                        level, scopedInterior, bounds);

        int interiorAirCount = best.enclosed() ? best.interiorAirCount() : 0;
        int width = best.enclosed() ? best.width() : 0;
        int length = best.enclosed() ? best.length() : 0;
        int height = best.enclosed() ? best.height() : 0;

        return new ScanResult(
            facilities.feedTroughCount(),
            facilities.autoFeedTroughCount(),
            facilities.hayHopperCount(),
            facilities.incubatorCount(),
            interiorAirCount,
            width,
            length,
            height,
            best.enclosed(),
            best.doorCount(),
            best.interiorMinX(),
            best.interiorMinY(),
            best.interiorMinZ(),
            best.interiorMaxX(),
            best.interiorMaxY(),
            best.interiorMaxZ(),
            Collections.unmodifiableSet(new LinkedHashSet<>(scopedInterior)),
            best.boundaryDoorCells()
        );
    }

    private static boolean isBetterComponent(ScanResult current, ScanResult best) {
        if (current.enclosed() != best.enclosed()) {
            return current.enclosed();
        }
        return current.interiorAirCount() > best.interiorAirCount();
    }

    private static ScanResult scanAirComponent(ServerLevel level,
                                               BlockPos start,
                                               int scanMinX,
                                               int scanMaxX,
                                               int scanMinY,
                                               int scanMaxY,
                                               int scanMinZ,
                                               int scanMaxZ) {

        Set<Long> visited = new HashSet<>();
        Set<Long> doors = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start.immutable());
        visited.add(start.asLong());

        int interiorMinX = start.getX();
        int interiorMaxX = start.getX();
        int interiorMinY = start.getY();
        int interiorMaxY = start.getY();
        int interiorMinZ = start.getZ();
        int interiorMaxZ = start.getZ();
        boolean enclosed = true;

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            interiorMinX = Math.min(interiorMinX, current.getX());
            interiorMaxX = Math.max(interiorMaxX, current.getX());
            interiorMinY = Math.min(interiorMinY, current.getY());
            interiorMaxY = Math.max(interiorMaxY, current.getY());
            interiorMinZ = Math.min(interiorMinZ, current.getZ());
            interiorMaxZ = Math.max(interiorMaxZ, current.getZ());

            if (isOnScanBoundary(current, scanMinX, scanMaxX, scanMinY, scanMaxY, scanMinZ, scanMaxZ)
                && !isAllowedBoundaryAir(level, current, scanMinY)) {
                enclosed = false;
            }

            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction);
                if (!withinScan(next, scanMinX, scanMaxX, scanMinY, scanMaxY, scanMinZ, scanMaxZ)) {
                    if (!isAllowedBoundaryAir(level, current, scanMinY)) {
                        enclosed = false;
                    }
                    continue;
                }

                BlockState nextState = level.getBlockState(next);
                if (nextState.isAir()) {
                    long key = next.asLong();
                    if (visited.add(key)) {
                        queue.add(next.immutable());
                    }
                } else if (nextState.is(BlockTags.DOORS) || nextState.is(BlockTags.FENCE_GATES)) {
                    doors.add(next.asLong());
                }
            }
        }

        int width = interiorMaxX - interiorMinX + 1;
        int length = interiorMaxZ - interiorMinZ + 1;
        int height = interiorMaxY - interiorMinY + 1;
        Set<Long> exteriorDoors =
                AnimalBuildingValidationRules.exteriorDoorCells(
                        level,
                        visited,
                        doors,
                        new AnimalBuildingValidationRules.Bounds(
                                scanMinX,
                                scanMaxX,
                                scanMinY,
                                scanMaxY,
                                scanMinZ,
                                scanMaxZ
                        )
                );

        return new ScanResult(
            0,
            0,
            0,
            0,
            visited.size(),
            width,
            length,
            height,
            enclosed,
            exteriorDoors.size(),
            interiorMinX,
            interiorMinY,
            interiorMinZ,
            interiorMaxX,
            interiorMaxY,
            interiorMaxZ,
            Collections.unmodifiableSet(new LinkedHashSet<>(visited)),
            exteriorDoors
        );
    }

    private static boolean withinScan(BlockPos pos, int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        return pos.getX() >= minX && pos.getX() <= maxX
            && pos.getY() >= minY && pos.getY() <= maxY
            && pos.getZ() >= minZ && pos.getZ() <= maxZ;
    }

    private static boolean isOnScanBoundary(BlockPos pos, int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        return pos.getX() == minX || pos.getX() == maxX
            || pos.getY() == minY || pos.getY() == maxY
            || pos.getZ() == minZ || pos.getZ() == maxZ;
    }

    private static boolean isAllowedBoundaryAir(ServerLevel level, BlockPos airPos, int scanMinY) {
        BlockPos cursor = airPos;
        for (int y = airPos.getY(); y >= scanMinY; y--) {
            BlockState state = level.getBlockState(cursor);
            if (state.isAir()) {
                cursor = cursor.below();
                continue;
            }
            return state.is(BlockTags.DOORS) || state.is(BlockTags.FENCE_GATES);
        }
        return false;
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
                            "coop", tier);
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
