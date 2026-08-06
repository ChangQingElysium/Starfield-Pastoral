package com.stardew.craft.animal.service;

import com.stardew.craft.animal.model.AnimalBuildingTierDefinition;
import com.stardew.craft.animal.model.AnimalBuildingTierDefinitions;
import com.stardew.craft.block.utility.AutoFeedTroughBlock;
import com.stardew.craft.block.utility.FeedTroughBlock;
import com.stardew.craft.block.utility.HayHopperBlock;
import com.stardew.craft.block.utility.IncubatorBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Shared, family-neutral rules around the Coop/Barn spatial scanners.
 *
 * <p>The two structures intentionally retain their existing flood-fill implementations, but
 * requirements, bounds, facility counting and failure ordering live here so gameplay rules cannot
 * drift between two copied validators.
 */
final class AnimalBuildingValidationRules {
    private static final int START_SEARCH_RADIUS_XZ = 2;
    private static final int START_SEARCH_RADIUS_Y = 1;

    private AnimalBuildingValidationRules() {
    }

    static Requirements requirementsFor(
            String family,
            int tier
    ) {
        String normalized = normalizeFamily(family);
        if (tier < 1 || tier > 3) {
            throw new IllegalArgumentException(
                    "Invalid " + normalized + " tier: " + tier);
        }
        if (!"coop".equals(normalized) && !"barn".equals(normalized)) {
            throw new IllegalArgumentException(
                    "Unsupported animal-building family: " + family);
        }
        AnimalBuildingTierDefinition.Validation validation =
                AnimalBuildingTierDefinitions.require(normalized, tier).validation();
        return new Requirements(
                validation.feedTroughs(),
                validation.autoFeedTroughs(),
                validation.hayHoppers(),
                validation.incubators(),
                validation.minInteriorBlocks(),
                validation.requireEnclosed(),
                validation.requireDoor(),
                validation.minDoorCount());
    }

    static Bounds boundsFor(
            BlockPos managerPos,
            String family,
            int tier,
            int minInteriorBlocks
    ) {
        String normalized = normalizeFamily(family);
        int inferredExtent = Math.max(
                8,
                (int) Math.ceil(Math.cbrt(
                        Math.max(1, minInteriorBlocks))) + 2);
        if (!"coop".equals(normalized) && !"barn".equals(normalized)) {
            throw new IllegalArgumentException(
                    "Unsupported animal-building family: " + family);
        }
        AnimalBuildingTierDefinition.Validation validation =
                AnimalBuildingTierDefinitions.require(normalized, tier).validation();
        int rangeXZ = Math.max(validation.scanRangeXZ(), inferredExtent);
        int rangeUp = Math.max(validation.scanRangeUp(), inferredExtent);
        int rangeDown = Math.max(validation.scanRangeDown(), inferredExtent);
        return new Bounds(
                managerPos.getX() - rangeXZ,
                managerPos.getX() + rangeXZ,
                managerPos.getY() - rangeDown,
                managerPos.getY() + rangeUp,
                managerPos.getZ() - rangeXZ,
                managerPos.getZ() + rangeXZ
        );
    }

    static List<BlockPos> collectStartCandidates(
            ServerLevel level,
            BlockPos managerPos,
            Bounds bounds
    ) {
        LinkedHashSet<Long> unique = new LinkedHashSet<>();
        List<BlockPos> candidates = new ArrayList<>();
        for (Direction direction : Direction.values()) {
            tryAddStartCandidate(
                    level,
                    managerPos.relative(direction),
                    bounds,
                    unique,
                    candidates);
        }
        for (int dx = -START_SEARCH_RADIUS_XZ;
             dx <= START_SEARCH_RADIUS_XZ;
             dx++) {
            for (int dy = -START_SEARCH_RADIUS_Y;
                 dy <= START_SEARCH_RADIUS_Y;
                 dy++) {
                for (int dz = -START_SEARCH_RADIUS_XZ;
                     dz <= START_SEARCH_RADIUS_XZ;
                     dz++) {
                    if ((dx == 0 && dy == 0 && dz == 0)
                            || Math.abs(dx) + Math.abs(dy)
                            + Math.abs(dz) <= 1) {
                        continue;
                    }
                    tryAddStartCandidate(
                            level,
                            managerPos.offset(dx, dy, dz),
                            bounds,
                            unique,
                            candidates);
                }
            }
        }
        return List.copyOf(candidates);
    }

    static FacilityCounts countInteriorFacilities(
            ServerLevel level,
            Set<Long> interiorAirCells,
            Bounds bounds
    ) {
        if (interiorAirCells.isEmpty()) {
            return FacilityCounts.EMPTY;
        }
        int feedTroughs = 0;
        int autoFeedTroughs = 0;
        int hayHoppers = 0;
        int incubators = 0;
        BlockPos.MutableBlockPos cursor =
                new BlockPos.MutableBlockPos();
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    cursor.set(x, y, z);
                    if (!isAdjacentToInteriorAir(
                            interiorAirCells, cursor)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(cursor);
                    if (state.getBlock() instanceof FeedTroughBlock) {
                        feedTroughs++;
                    } else if (state.getBlock()
                            instanceof AutoFeedTroughBlock) {
                        autoFeedTroughs++;
                    } else if (state.getBlock()
                            instanceof HayHopperBlock
                            && state.getValue(HayHopperBlock.PART)
                            == HayHopperBlock.Part.MAIN) {
                        hayHoppers++;
                    } else if (state.getBlock()
                            instanceof IncubatorBlock
                            && state.getValue(IncubatorBlock.PART)
                            == IncubatorBlock.Part.MAIN) {
                        incubators++;
                    }
                }
            }
        }
        return new FacilityCounts(
                feedTroughs,
                autoFeedTroughs,
                hayHoppers,
                incubators
        );
    }

    static Set<Long> exteriorDoorCells(
            ServerLevel level,
            Set<Long> interiorAirCells,
            Set<Long> candidateDoorCells,
            Bounds bounds
    ) {
        return exteriorDoorCells(
                interiorAirCells,
                candidateDoorCells,
                bounds,
                new DoorEnvironment() {
                    @Override
                    public boolean isAir(BlockPos pos) {
                        return level.getBlockState(pos).isAir();
                    }

                    @Override
                    public List<Direction> passageDirections(
                            BlockPos pos
                    ) {
                        BlockState state = level.getBlockState(pos);
                        if (state.hasProperty(
                                BlockStateProperties.HORIZONTAL_FACING)) {
                            Direction facing = state.getValue(
                                    BlockStateProperties.HORIZONTAL_FACING);
                            return List.of(facing, facing.getOpposite());
                        }
                        return List.of(
                                Direction.NORTH,
                                Direction.SOUTH,
                                Direction.WEST,
                                Direction.EAST
                        );
                    }
                }
        );
    }

    static Set<Long> exteriorDoorCells(
            Set<Long> interiorAirCells,
            Set<Long> candidateDoorCells,
            Bounds bounds,
            DoorEnvironment environment
    ) {
        if (interiorAirCells.isEmpty()
                || candidateDoorCells.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<Long> exterior = new LinkedHashSet<>();
        for (long packedDoor : candidateDoorCells) {
            BlockPos door = BlockPos.of(packedDoor);
            for (Direction direction
                    : environment.passageDirections(door)) {
                BlockPos inside = door.relative(direction);
                BlockPos outside =
                        door.relative(direction.getOpposite());
                if (!interiorAirCells.contains(inside.asLong())
                        || interiorAirCells.contains(outside.asLong())
                        || !environment.isAir(outside)) {
                    continue;
                }
                if (airReachesScanBoundary(
                        outside, bounds, environment)) {
                    exterior.add(packedDoor);
                    break;
                }
            }
        }
        return Set.copyOf(exterior);
    }

    private static boolean airReachesScanBoundary(
            BlockPos start,
            Bounds bounds,
            DoorEnvironment environment
    ) {
        if (!bounds.contains(start)) {
            return true;
        }
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        HashSet<Long> visited = new HashSet<>();
        queue.add(start.immutable());
        visited.add(start.asLong());
        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            if (bounds.isBoundary(current)) {
                return true;
            }
            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction);
                if (!bounds.contains(next)) {
                    return true;
                }
                if (environment.isAir(next)
                        && visited.add(next.asLong())) {
                    queue.addLast(next.immutable());
                }
            }
        }
        return false;
    }

    static ValidationOutcome evaluate(
            String family,
            Requirements requirements,
            ScanFacts facts
    ) {
        ArrayList<Failure> failures = new ArrayList<>();
        if (!facts.hasInteriorSpace()) {
            failures.add(new Failure(
                    FailureType.NO_INTERIOR, 1, 0));
        }
        addShortfall(
                failures,
                FailureType.FEED_TROUGHS,
                requirements.feedTroughCount(),
                facts.feedTroughCount());
        addShortfall(
                failures,
                FailureType.AUTO_FEED_TROUGHS,
                requirements.autoFeedTroughCount(),
                facts.autoFeedTroughCount());
        addShortfall(
                failures,
                FailureType.HAY_HOPPERS,
                requirements.hayHopperCount(),
                facts.hayHopperCount());
        addShortfall(
                failures,
                FailureType.INCUBATORS,
                requirements.incubatorCount(),
                facts.incubatorCount());
        addShortfall(
                failures,
                FailureType.INTERIOR_SIZE,
                requirements.minInteriorBlocks(),
                facts.interiorAirCount());
        if (requirements.requireEnclosed()
                && !facts.enclosed()) {
            failures.add(new Failure(
                    FailureType.NOT_ENCLOSED, 1, 0));
        }
        if (requirements.requireDoor()) {
            addShortfall(
                    failures,
                    FailureType.DOORS,
                    requirements.minDoorCount(),
                    facts.doorCount());
        }
        return new ValidationOutcome(
                failures.isEmpty(),
                messageFor(family, failures),
                List.copyOf(failures)
        );
    }

    private static void tryAddStartCandidate(
            ServerLevel level,
            BlockPos candidate,
            Bounds bounds,
            Set<Long> unique,
            List<BlockPos> output
    ) {
        if (bounds.contains(candidate)
                && level.getBlockState(candidate).isAir()
                && unique.add(candidate.asLong())) {
            output.add(candidate.immutable());
        }
    }

    private static boolean isAdjacentToInteriorAir(
            Set<Long> interiorAirCells,
            BlockPos facilityPos
    ) {
        for (Direction direction : Direction.values()) {
            if (interiorAirCells.contains(
                    facilityPos.relative(direction).asLong())) {
                return true;
            }
        }
        return false;
    }

    private static void addShortfall(
            List<Failure> failures,
            FailureType type,
            int required,
            int actual
    ) {
        if (actual < required) {
            failures.add(new Failure(type, required, actual));
        }
    }

    private static Component messageFor(
            String family,
            List<Failure> failures
    ) {
        if (failures.isEmpty()) {
            return Component.translatable(
                    "stardewcraft.manager.validation.success");
        }
        String normalized = normalizeFamily(family);
        MutableComponent message = Component.empty();
        for (int index = 0; index < failures.size(); index++) {
            if (index > 0) {
                message.append(Component.translatable(
                        "stardewcraft.manager.validation.separator"));
            }
            Failure failure = failures.get(index);
            message.append(switch (failure.type()) {
                case NO_INTERIOR -> Component.translatable(
                        "stardewcraft.manager." + normalized
                                + ".validation.no_interior");
                case FEED_TROUGHS -> Component.translatable(
                        "stardewcraft.manager.validation.feed_troughs",
                        failure.required(), failure.actual());
                case AUTO_FEED_TROUGHS -> Component.translatable(
                        "stardewcraft.manager.validation.auto_feed_troughs",
                        failure.required(), failure.actual());
                case HAY_HOPPERS -> Component.translatable(
                        "stardewcraft.manager.validation.hay_hoppers",
                        failure.required(), failure.actual());
                case INCUBATORS -> Component.translatable(
                        "stardewcraft.manager.validation.incubators",
                        failure.required(), failure.actual());
                case INTERIOR_SIZE -> Component.translatable(
                        "stardewcraft.manager." + normalized
                                + ".validation.interior_size",
                        failure.required(), failure.actual());
                case NOT_ENCLOSED -> Component.translatable(
                        "stardewcraft.manager." + normalized
                                + ".validation.not_enclosed");
                case DOORS -> Component.translatable(
                        "stardewcraft.manager.validation.doors",
                        failure.required(), failure.actual());
            });
        }
        return message;
    }

    private static String normalizeFamily(String family) {
        return family == null
                ? ""
                : family.trim().toLowerCase(Locale.ROOT);
    }

    record Requirements(
            int feedTroughCount,
            int autoFeedTroughCount,
            int hayHopperCount,
            int incubatorCount,
            int minInteriorBlocks,
            boolean requireEnclosed,
            boolean requireDoor,
            int minDoorCount
    ) {
    }

    record ScanFacts(
            int feedTroughCount,
            int autoFeedTroughCount,
            int hayHopperCount,
            int incubatorCount,
            int interiorAirCount,
            int width,
            int length,
            int height,
            boolean enclosed,
            int doorCount
    ) {
        boolean hasInteriorSpace() {
            return width > 0 && length > 0 && height > 0;
        }
    }

    record Bounds(
            int minX,
            int maxX,
            int minY,
            int maxY,
            int minZ,
            int maxZ
    ) {
        boolean contains(BlockPos pos) {
            return pos.getX() >= minX
                    && pos.getX() <= maxX
                    && pos.getY() >= minY
                    && pos.getY() <= maxY
                    && pos.getZ() >= minZ
                    && pos.getZ() <= maxZ;
        }

        boolean isBoundary(BlockPos pos) {
            return pos.getX() == minX
                    || pos.getX() == maxX
                    || pos.getY() == minY
                    || pos.getY() == maxY
                    || pos.getZ() == minZ
                    || pos.getZ() == maxZ;
        }
    }

    interface DoorEnvironment {
        boolean isAir(BlockPos pos);

        List<Direction> passageDirections(BlockPos pos);
    }

    record FacilityCounts(
            int feedTroughCount,
            int autoFeedTroughCount,
            int hayHopperCount,
            int incubatorCount
    ) {
        private static final FacilityCounts EMPTY =
                new FacilityCounts(0, 0, 0, 0);
    }

    record ValidationOutcome(
            boolean success,
            Component message,
            List<Failure> failures
    ) {
    }

    record Failure(
            FailureType type,
            int required,
            int actual
    ) {
    }

    enum FailureType {
        NO_INTERIOR,
        FEED_TROUGHS,
        AUTO_FEED_TROUGHS,
        HAY_HOPPERS,
        INCUBATORS,
        INTERIOR_SIZE,
        NOT_ENCLOSED,
        DOORS
    }
}
