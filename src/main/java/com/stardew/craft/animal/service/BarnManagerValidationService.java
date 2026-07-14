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
public final class BarnManagerValidationService {
    private static final int START_SEARCH_RADIUS_XZ = 2;
    private static final int START_SEARCH_RADIUS_Y = 1;

    private BarnManagerValidationService() {
    }

    public static ValidationResult validateForTier(ServerLevel level, BlockPos managerPos, int targetTier) {
        TierRequirement requirement = TierRequirement.fromTier(targetTier);
        ScanResult scan = scan(level, managerPos, requirement.minInteriorBlocks());

        List<Component> reasons = new ArrayList<>();

        if (!scan.hasInteriorSpace()) {
            reasons.add(Component.translatable("stardewcraft.manager.barn.validation.no_interior"));
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
            reasons.add(Component.translatable("stardewcraft.manager.barn.validation.interior_size",
                    requirement.minInteriorBlocks(), scan.interiorAirCount()));
        }

        if (Config.BARN_REQUIRE_ENCLOSED.get() && !scan.enclosed()) {
            reasons.add(Component.translatable("stardewcraft.manager.barn.validation.not_enclosed"));
        }

        if (Config.BARN_REQUIRE_DOOR.get() && scan.doorCount() < Config.BARN_MIN_DOOR_COUNT.get()) {
            reasons.add(Component.translatable("stardewcraft.manager.validation.doors",
                    Config.BARN_MIN_DOOR_COUNT.get(), scan.doorCount()));
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
        int rangeXZ = Math.max(Config.BARN_SCAN_RANGE_XZ.get(), inferredExtent);
        int rangeUp = Math.max(Config.BARN_SCAN_RANGE_UP.get(), inferredExtent);
        int rangeDown = Math.max(Config.BARN_SCAN_RANGE_DOWN.get(), inferredExtent);

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
        FacilityCount facilities = countInteriorFacilities(level, scopedInterior, scanMinX, scanMaxX, scanMinY, scanMaxY, scanMinZ, scanMaxZ);

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
            if (interiorAirCells.contains(pack(facilityPos.relative(direction)))) {
                return true;
            }
        }
        return false;
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
        int doorCount = 0;

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
                    if (boundaryDoorCells.add(doorKey)) {
                        doorCount++;
                    }
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

        return new FloodResult(interiorAirCells.size(), enclosed, doorCount, minX, minY, minZ, maxX, maxY, maxZ, interiorAirCells, boundaryDoorCells);
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
    }

    public record TierRequirement(int feedTroughCount,
                                  int autoFeedTroughCount,
                                  int hayHopperCount,
                                  int incubatorCount,
                                  int minInteriorBlocks) {
        public static TierRequirement fromTier(int tier) {
            return switch (tier) {
                case 1 -> new TierRequirement(
                    Config.BARN_T1_FEED_TROUGH.get(),
                    Config.BARN_T1_AUTOFEED_TROUGH.get(),
                    Config.BARN_T1_HAY_HOPPER.get(),
                    Config.BARN_T1_INCUBATOR.get(),
                    Config.BARN_T1_MIN_INTERIOR_BLOCKS.get()
                );
                case 2 -> new TierRequirement(
                    Config.BARN_T2_FEED_TROUGH.get(),
                    Config.BARN_T2_AUTOFEED_TROUGH.get(),
                    Config.BARN_T2_HAY_HOPPER.get(),
                    Config.BARN_T2_INCUBATOR.get(),
                    Config.BARN_T2_MIN_INTERIOR_BLOCKS.get()
                );
                case 3 -> new TierRequirement(
                    Config.BARN_T3_FEED_TROUGH.get(),
                    Config.BARN_T3_AUTOFEED_TROUGH.get(),
                    Config.BARN_T3_HAY_HOPPER.get(),
                    Config.BARN_T3_INCUBATOR.get(),
                    Config.BARN_T3_MIN_INTERIOR_BLOCKS.get()
                );
                default -> throw new IllegalArgumentException("Invalid barn tier: " + tier);
            };
        }
    }

    private record FacilityCount(int feedTroughCount,
                                 int autoFeedTroughCount,
                                 int hayHopperCount,
                                 int incubatorCount) {
    }
}
