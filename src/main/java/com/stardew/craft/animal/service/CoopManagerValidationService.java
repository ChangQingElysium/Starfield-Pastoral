package com.stardew.craft.animal.service;

import com.stardew.craft.Config;
import com.stardew.craft.block.utility.AutoFeedTroughBlock;
import com.stardew.craft.block.utility.FeedTroughBlock;
import com.stardew.craft.block.utility.HayHopperBlock;
import com.stardew.craft.block.utility.IncubatorBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@SuppressWarnings("null")
public final class CoopManagerValidationService {
    private static final int START_SEARCH_RADIUS_XZ = 2;
    private static final int START_SEARCH_RADIUS_Y = 1;

    private CoopManagerValidationService() {
    }

    public static ValidationResult validateForTier(ServerLevel level, BlockPos managerPos, int targetTier) {
        TierRequirement requirement = TierRequirement.fromTier(targetTier);
        ScanResult scan = scan(level, managerPos, requirement.minInteriorBlocks());

        List<Component> reasons = new ArrayList<>();

        if (!scan.hasInteriorSpace()) {
            reasons.add(Component.translatable("stardewcraft.manager.coop.validation.no_interior"));
        }

        if (scan.feedTroughCount() < requirement.feedTroughCount()) {
            reasons.add(Component.translatable("stardewcraft.manager.validation.feed_troughs",
                    requirement.feedTroughCount(), scan.feedTroughCount()));
        }

        if (scan.autoFeedTroughCount() < requirement.autoFeedTroughCount()) {
            reasons.add(Component.translatable("stardewcraft.manager.validation.auto_feed_troughs",
                    requirement.autoFeedTroughCount(), scan.autoFeedTroughCount()));
        }

        if (scan.hayHopperCount() < requirement.hayHopperCount()) {
            reasons.add(Component.translatable("stardewcraft.manager.validation.hay_hoppers",
                    requirement.hayHopperCount(), scan.hayHopperCount()));
        }

        if (scan.incubatorCount() < requirement.incubatorCount()) {
            reasons.add(Component.translatable("stardewcraft.manager.validation.incubators",
                    requirement.incubatorCount(), scan.incubatorCount()));
        }

        if (scan.interiorAirCount() < requirement.minInteriorBlocks()) {
            reasons.add(Component.translatable("stardewcraft.manager.coop.validation.interior_size",
                    requirement.minInteriorBlocks(), scan.interiorAirCount()));
        }

        if (Config.COOP_REQUIRE_ENCLOSED.get() && !scan.enclosed()) {
            reasons.add(Component.translatable("stardewcraft.manager.coop.validation.not_enclosed"));
        }

        if (Config.COOP_REQUIRE_DOOR.get() && scan.doorCount() < Config.COOP_MIN_DOOR_COUNT.get()) {
            reasons.add(Component.translatable("stardewcraft.manager.validation.doors",
                    Config.COOP_MIN_DOOR_COUNT.get(), scan.doorCount()));
        }

        boolean ok = reasons.isEmpty();
        Component message = ok
                ? Component.translatable("stardewcraft.manager.validation.success")
                : joinReasons(reasons);
        return new ValidationResult(ok, targetTier, requirement, scan, message);
    }

    private static Component joinReasons(List<Component> reasons) {
        MutableComponent message = Component.empty();
        for (int i = 0; i < reasons.size(); i++) {
            if (i > 0) message.append(Component.translatable("stardewcraft.manager.validation.separator"));
            message.append(reasons.get(i));
        }
        return message;
    }

    private static ScanResult scan(ServerLevel level, BlockPos managerPos, int minInteriorBlocks) {
        int inferredExtent = Math.max(8, (int) Math.ceil(Math.cbrt(Math.max(1, minInteriorBlocks))) + 2);
        int rangeXZ = Math.max(Config.COOP_SCAN_RANGE_XZ.get(), inferredExtent);
        int rangeUp = Math.max(Config.COOP_SCAN_RANGE_UP.get(), inferredExtent);
        int rangeDown = Math.max(Config.COOP_SCAN_RANGE_DOWN.get(), inferredExtent);

        int scanMinX = managerPos.getX() - rangeXZ;
        int scanMaxX = managerPos.getX() + rangeXZ;
        int scanMinY = managerPos.getY() - rangeDown;
        int scanMaxY = managerPos.getY() + rangeUp;
        int scanMinZ = managerPos.getZ() - rangeXZ;
        int scanMaxZ = managerPos.getZ() + rangeXZ;

        List<BlockPos> startCandidates = collectStartCandidates(level, managerPos, scanMinX, scanMaxX, scanMinY, scanMaxY, scanMinZ, scanMaxZ);

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
        FacilityCount facilities = countInteriorFacilities(level, scopedInterior, scanMinX, scanMaxX, scanMinY, scanMaxY, scanMinZ, scanMaxZ);

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

    private static List<BlockPos> collectStartCandidates(ServerLevel level,
                                                         BlockPos managerPos,
                                                         int scanMinX,
                                                         int scanMaxX,
                                                         int scanMinY,
                                                         int scanMaxY,
                                                         int scanMinZ,
                                                         int scanMaxZ) {
        LinkedHashSet<Long> unique = new LinkedHashSet<>();
        List<BlockPos> startCandidates = new ArrayList<>();

        for (Direction direction : Direction.values()) {
            BlockPos candidate = managerPos.relative(direction);
            if (tryAddStartCandidate(level, candidate, scanMinX, scanMaxX, scanMinY, scanMaxY, scanMinZ, scanMaxZ, unique)) {
                startCandidates.add(candidate.immutable());
            }
        }

        for (int dx = -START_SEARCH_RADIUS_XZ; dx <= START_SEARCH_RADIUS_XZ; dx++) {
            for (int dy = -START_SEARCH_RADIUS_Y; dy <= START_SEARCH_RADIUS_Y; dy++) {
                for (int dz = -START_SEARCH_RADIUS_XZ; dz <= START_SEARCH_RADIUS_XZ; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) <= 1) {
                        continue;
                    }
                    BlockPos candidate = managerPos.offset(dx, dy, dz);
                    if (tryAddStartCandidate(level, candidate, scanMinX, scanMaxX, scanMinY, scanMaxY, scanMinZ, scanMaxZ, unique)) {
                        startCandidates.add(candidate.immutable());
                    }
                }
            }
        }

        return startCandidates;
    }

    private static boolean tryAddStartCandidate(ServerLevel level,
                                                BlockPos candidate,
                                                int scanMinX,
                                                int scanMaxX,
                                                int scanMinY,
                                                int scanMaxY,
                                                int scanMinZ,
                                                int scanMaxZ,
                                                Set<Long> unique) {
        if (!withinScan(candidate, scanMinX, scanMaxX, scanMinY, scanMaxY, scanMinZ, scanMaxZ)) {
            return false;
        }
        if (!level.getBlockState(candidate).isAir()) {
            return false;
        }
        return unique.add(candidate.asLong());
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
            doors.size(),
            interiorMinX,
            interiorMinY,
            interiorMinZ,
            interiorMaxX,
            interiorMaxY,
            interiorMaxZ,
            Collections.unmodifiableSet(new LinkedHashSet<>(visited)),
            Collections.unmodifiableSet(new LinkedHashSet<>(doors))
        );
    }

    private static FacilityCount countInteriorFacilities(ServerLevel level,
                                                         Set<Long> interiorAirCells,
                                                         int scanMinX,
                                                         int scanMaxX,
                                                         int scanMinY,
                                                         int scanMaxY,
                                                         int scanMinZ,
                                                         int scanMaxZ) {
        if (interiorAirCells.isEmpty()) {
            return new FacilityCount(0, 0, 0, 0);
        }

        int feedTroughCount = 0;
        int autoFeedTroughCount = 0;
        int hayHopperCount = 0;
        int incubatorCount = 0;

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = scanMinX; x <= scanMaxX; x++) {
            for (int y = scanMinY; y <= scanMaxY; y++) {
                for (int z = scanMinZ; z <= scanMaxZ; z++) {
                    cursor.set(x, y, z);
                    BlockState state = level.getBlockState(cursor);
                    if (!isAdjacentToInteriorAir(interiorAirCells, cursor)) {
                        continue;
                    }
                    if (state.getBlock() instanceof FeedTroughBlock) {
                        feedTroughCount++;
                        continue;
                    }
                    if (state.getBlock() instanceof AutoFeedTroughBlock) {
                        autoFeedTroughCount++;
                        continue;
                    }
                    if (state.getBlock() instanceof HayHopperBlock) {
                        if (state.getValue(HayHopperBlock.PART) == HayHopperBlock.Part.MAIN) {
                            hayHopperCount++;
                        }
                        continue;
                    }
                    if (state.getBlock() instanceof IncubatorBlock
                        && state.getValue(IncubatorBlock.PART) == IncubatorBlock.Part.MAIN) {
                        incubatorCount++;
                    }
                }
            }
        }

        return new FacilityCount(feedTroughCount, autoFeedTroughCount, hayHopperCount, incubatorCount);
    }

    private static boolean isAdjacentToInteriorAir(Set<Long> interiorAirCells, BlockPos facilityPos) {
        for (Direction direction : Direction.values()) {
            if (interiorAirCells.contains(facilityPos.relative(direction).asLong())) {
                return true;
            }
        }
        return false;
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
    }

    public record TierRequirement(int feedTroughCount,
                                  int autoFeedTroughCount,
                                  int hayHopperCount,
                                  int incubatorCount,
                                  int minInteriorBlocks) {
        public static TierRequirement fromTier(int tier) {
            return switch (tier) {
                case 1 -> new TierRequirement(
                    Config.COOP_T1_FEED_TROUGH.get(),
                    Config.COOP_T1_AUTOFEED_TROUGH.get(),
                    Config.COOP_T1_HAY_HOPPER.get(),
                    Config.COOP_T1_INCUBATOR.get(),
                    Config.COOP_T1_MIN_INTERIOR_BLOCKS.get()
                );
                case 2 -> new TierRequirement(
                    Config.COOP_T2_FEED_TROUGH.get(),
                    Config.COOP_T2_AUTOFEED_TROUGH.get(),
                    Config.COOP_T2_HAY_HOPPER.get(),
                    Config.COOP_T2_INCUBATOR.get(),
                    Config.COOP_T2_MIN_INTERIOR_BLOCKS.get()
                );
                case 3 -> new TierRequirement(
                    Config.COOP_T3_FEED_TROUGH.get(),
                    Config.COOP_T3_AUTOFEED_TROUGH.get(),
                    Config.COOP_T3_HAY_HOPPER.get(),
                    Config.COOP_T3_INCUBATOR.get(),
                    Config.COOP_T3_MIN_INTERIOR_BLOCKS.get()
                );
                default -> throw new IllegalArgumentException("Invalid coop tier: " + tier);
            };
        }
    }

    private record FacilityCount(int feedTroughCount,
                                 int autoFeedTroughCount,
                                 int hayHopperCount,
                                 int incubatorCount) {
    }
}
