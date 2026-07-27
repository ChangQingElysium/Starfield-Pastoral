package com.stardew.craft.animal.service;

import com.stardew.craft.animal.model.AnimalBuildingRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

@SuppressWarnings("null")
public final class AnimalDoorStateService {
    private static final Map<ServerLevel, Map<String, CachedDoors>> LEGACY_DOOR_CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());

    private AnimalDoorStateService() {
    }

    /**
     * Returns the validated door cells, or a revision-scoped compatibility index for old records
     * which predate persisted boundary cells. The expensive bounding-volume fallback therefore
     * runs at most once per building revision, never once per animal.
     */
    public static List<BlockPos> boundaryDoorPositions(
            ServerLevel level,
            AnimalBuildingRecord building
    ) {
        if (level == null || building == null) {
            return List.of();
        }
        if (!building.boundaryDoorCells().isEmpty()) {
            return building.boundaryDoorCells().stream()
                    .map(BlockPos::of)
                    .map(BlockPos::immutable)
                    .toList();
        }

        synchronized (LEGACY_DOOR_CACHE) {
            Map<String, CachedDoors> levelCache =
                    LEGACY_DOOR_CACHE.computeIfAbsent(
                            level, ignored -> new HashMap<>());
            CachedDoors cached =
                    levelCache.get(building.buildingId());
            if (cached != null
                    && cached.matches(building)) {
                return cached.positions();
            }

            java.util.ArrayList<BlockPos> discovered =
                    new java.util.ArrayList<>();
            for (int y = building.minY() - 1;
                 y <= building.maxY() + 1;
                 y++) {
                for (int z = building.minZ() - 1;
                     z <= building.maxZ() + 1;
                     z++) {
                    for (int x = building.minX() - 1;
                         x <= building.maxX() + 1;
                         x++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        BlockState state =
                                level.getBlockState(pos);
                        if (isDoorOrFenceGate(state)
                                && isBuildingBoundaryPortal(
                                building, pos, state)) {
                            discovered.add(pos);
                        }
                    }
                }
            }
            List<BlockPos> positions =
                    List.copyOf(discovered);
            levelCache.put(
                    building.buildingId(),
                    CachedDoors.from(building, positions));
            return positions;
        }
    }

    public static boolean isAnyBoundaryDoorOpen(ServerLevel level, AnimalBuildingRecord building) {
        if (level == null || building == null) {
            return false;
        }

        for (BlockPos pos : boundaryDoorPositions(level, building)) {
            BlockState state = level.getBlockState(pos);
            if (isDoorOrFenceGate(state) && isOpen(state)) {
                return true;
            }
        }
        return false;
    }

    public static int setBoundaryDoorsOpen(ServerLevel level, AnimalBuildingRecord building, boolean open) {
        if (level == null || building == null) {
            return 0;
        }

        int changed = 0;
        for (BlockPos pos : boundaryDoorPositions(level, building)) {
            BlockState state = level.getBlockState(pos);
            if (!isDoorOrFenceGate(state)
                    || !state.hasProperty(
                    BlockStateProperties.OPEN)) {
                continue;
            }
            boolean current = Boolean.TRUE.equals(
                    state.getValue(BlockStateProperties.OPEN));
            if (current == open) {
                continue;
            }
            level.setBlock(
                    pos,
                    state.setValue(
                            BlockStateProperties.OPEN, open),
                    3);
            changed++;
        }
        return changed;
    }

    public static boolean isDoorOrFenceGate(BlockState state) {
        return state.is(BlockTags.DOORS) || state.is(BlockTags.FENCE_GATES);
    }

    public static boolean isOpen(BlockState state) {
        return state.hasProperty(BlockStateProperties.OPEN) && Boolean.TRUE.equals(state.getValue(BlockStateProperties.OPEN));
    }

    private static boolean isBuildingBoundaryPortal(
            AnimalBuildingRecord building,
            BlockPos door,
            BlockState state
    ) {
        if (state.hasProperty(
                BlockStateProperties.HORIZONTAL_FACING)) {
            Direction facing = state.getValue(
                    BlockStateProperties.HORIZONTAL_FACING);
            return isOppositeSidesOfBoundary(
                    building,
                    door.relative(facing),
                    door.relative(facing.getOpposite())
            );
        }
        return isOppositeSidesOfBoundary(
                building,
                door.north(),
                door.south()
        ) || isOppositeSidesOfBoundary(
                building,
                door.west(),
                door.east()
        );
    }

    private static boolean isOppositeSidesOfBoundary(
            AnimalBuildingRecord building,
            BlockPos sideA,
            BlockPos sideB
    ) {
        return building.isInBounds(sideA)
                != building.isInBounds(sideB);
    }

    private record CachedDoors(
            long structureRevision,
            int minX,
            int minY,
            int minZ,
            int maxX,
            int maxY,
            int maxZ,
            List<BlockPos> positions
    ) {
        private static CachedDoors from(
                AnimalBuildingRecord building,
                List<BlockPos> positions
        ) {
            return new CachedDoors(
                    building.structureRevision(),
                    building.minX(),
                    building.minY(),
                    building.minZ(),
                    building.maxX(),
                    building.maxY(),
                    building.maxZ(),
                    positions
            );
        }

        private boolean matches(
                AnimalBuildingRecord building
        ) {
            return structureRevision
                    == building.structureRevision()
                    && minX == building.minX()
                    && minY == building.minY()
                    && minZ == building.minZ()
                    && maxX == building.maxX()
                    && maxY == building.maxY()
                    && maxZ == building.maxZ();
        }
    }
}
