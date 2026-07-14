package com.stardew.craft.fishpond.service;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.fishpond.data.FishPondWorldData;
import com.stardew.craft.fishpond.model.FishPondRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@SuppressWarnings("null")
public final class FishPondManagerValidationService {
    private static final int MIN_WATER_CELLS = 27;
    private static final int MIN_WATER_WIDTH = 3;
    private static final int MIN_WATER_LENGTH = 3;
    private static final int START_SEARCH_RADIUS_XZ = 8;
    private static final int START_SEARCH_RADIUS_Y = 4;
    private static final int SCAN_RANGE_XZ = 16;
    private static final int SCAN_RANGE_UP = 6;
    private static final int SCAN_RANGE_DOWN = 6;

    private FishPondManagerValidationService() {
    }

    public static ValidationResult validate(ServerLevel level, BlockPos managerPos) {
        ScanResult scan = scan(level, managerPos);
        List<Component> reasons = new ArrayList<>();
        FishPondWorldData worldData = FishPondWorldData.get(level);

        if (scan.waterCells().isEmpty()) {
            reasons.add(Component.translatable("stardewcraft.manager.fish_pond.validation.no_water"));
        } else {
            if (scan.waterCells().size() < MIN_WATER_CELLS) {
                reasons.add(Component.translatable("stardewcraft.manager.fish_pond.validation.water_cells",
                        MIN_WATER_CELLS, scan.waterCells().size()));
            }
            if (scan.width() < MIN_WATER_WIDTH || scan.length() < MIN_WATER_LENGTH) {
                reasons.add(Component.translatable("stardewcraft.manager.fish_pond.validation.dimensions",
                        MIN_WATER_WIDTH, MIN_WATER_LENGTH, scan.width(), scan.length()));
            }
        }
        if (scan.netPositions().isEmpty()) {
            reasons.add(Component.translatable("stardewcraft.manager.fish_pond.validation.no_net"));
        }
        if (scan.bucketPositions().isEmpty()) {
            reasons.add(Component.translatable("stardewcraft.manager.fish_pond.validation.no_bucket"));
        } else if (scan.bucketPositions().size() > 1) {
            reasons.add(Component.translatable("stardewcraft.manager.fish_pond.validation.bucket_count",
                    scan.bucketPositions().size()));
        }

        if (!scan.waterCells().isEmpty()) {
            for (FishPondRecord existing : worldData.getPonds()) {
                if (!level.dimension().location().toString().equals(existing.dimensionId())) {
                    continue;
                }
                if (existing.managerPos().equals(managerPos)) {
                    continue;
                }
                if (existing.containsPosInEnvelope(managerPos)) {
                    reasons.add(Component.translatable("stardewcraft.manager.fish_pond.validation.manager_overlap", existing.pondId()));
                    break;
                }
                if (scan.intersectsEnvelope(existing)) {
                    reasons.add(Component.translatable("stardewcraft.manager.fish_pond.validation.water_overlap", existing.pondId()));
                    break;
                }
            }
        }

        for (FishPondRecord existing : worldData.getPonds()) {
            if (!level.dimension().location().toString().equals(existing.dimensionId())) {
                continue;
            }
            if (existing.managerPos().equals(managerPos)) {
                continue;
            }
            if (scan.bucketPositions().contains(existing.bucketPos())) {
                reasons.add(Component.translatable("stardewcraft.manager.fish_pond.validation.bucket_in_use", existing.pondId()));
                break;
            }
            if (!Collections.disjoint(scan.netPositions(), existing.netPositions())) {
                reasons.add(Component.translatable("stardewcraft.manager.fish_pond.validation.net_in_use", existing.pondId()));
                break;
            }
        }

        boolean ok = reasons.isEmpty();
        Component message = ok
            ? Component.translatable("stardewcraft.manager.fish_pond.validation.success")
            : joinReasons(reasons);
        return new ValidationResult(ok, scan, message);
    }

    private static Component joinReasons(List<Component> reasons) {
        MutableComponent message = Component.empty();
        for (int i = 0; i < reasons.size(); i++) {
            if (i > 0) message.append(Component.translatable("stardewcraft.manager.validation.separator"));
            message.append(reasons.get(i));
        }
        return message;
    }

    private static ScanResult scan(ServerLevel level, BlockPos managerPos) {
        int scanMinX = managerPos.getX() - SCAN_RANGE_XZ;
        int scanMaxX = managerPos.getX() + SCAN_RANGE_XZ;
        int scanMinY = managerPos.getY() - SCAN_RANGE_DOWN;
        int scanMaxY = managerPos.getY() + SCAN_RANGE_UP;
        int scanMinZ = managerPos.getZ() - SCAN_RANGE_XZ;
        int scanMaxZ = managerPos.getZ() + SCAN_RANGE_XZ;

        List<BlockPos> startCandidates = collectWaterStartCandidates(level, managerPos, scanMinX, scanMaxX, scanMinY, scanMaxY, scanMinZ, scanMaxZ);
        WaterComponent best = null;
        for (BlockPos start : startCandidates) {
            WaterComponent current = scanWaterComponent(level, start, scanMinX, scanMaxX, scanMinY, scanMaxY, scanMinZ, scanMaxZ);
            if (best == null || current.waterCells().size() > best.waterCells().size()) {
                best = current;
            }
        }

        if (best == null) {
            return new ScanResult(
                Collections.emptySet(),
                Collections.emptySet(),
                Collections.emptySet(),
                managerPos.getX(),
                managerPos.getY(),
                managerPos.getZ(),
                managerPos.getX(),
                managerPos.getY(),
                managerPos.getZ()
            );
        }

        FacilityResult facilities = collectFacilities(level, best);
        return new ScanResult(
            Collections.unmodifiableSet(new LinkedHashSet<>(best.waterCells())),
            Collections.unmodifiableSet(new LinkedHashSet<>(facilities.netPositions())),
            Collections.unmodifiableSet(new LinkedHashSet<>(facilities.bucketPositions())),
            best.minX(),
            best.minY(),
            best.minZ(),
            best.maxX(),
            best.maxY(),
            best.maxZ()
        );
    }

    private static List<BlockPos> collectWaterStartCandidates(ServerLevel level,
                                                              BlockPos managerPos,
                                                              int scanMinX,
                                                              int scanMaxX,
                                                              int scanMinY,
                                                              int scanMaxY,
                                                              int scanMinZ,
                                                              int scanMaxZ) {
        LinkedHashSet<Long> unique = new LinkedHashSet<>();
        List<BlockPos> candidates = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -START_SEARCH_RADIUS_XZ; dx <= START_SEARCH_RADIUS_XZ; dx++) {
            for (int dy = -START_SEARCH_RADIUS_Y; dy <= START_SEARCH_RADIUS_Y; dy++) {
                for (int dz = -START_SEARCH_RADIUS_XZ; dz <= START_SEARCH_RADIUS_XZ; dz++) {
                    cursor.set(managerPos.getX() + dx, managerPos.getY() + dy, managerPos.getZ() + dz);
                    if (!withinScan(cursor, scanMinX, scanMaxX, scanMinY, scanMaxY, scanMinZ, scanMaxZ)) {
                        continue;
                    }
                    if (!isWater(level, cursor)) {
                        continue;
                    }
                    long key = cursor.asLong();
                    if (unique.add(key)) {
                        candidates.add(cursor.immutable());
                    }
                }
            }
        }
        return candidates;
    }

    private static WaterComponent scanWaterComponent(ServerLevel level,
                                                     BlockPos start,
                                                     int scanMinX,
                                                     int scanMaxX,
                                                     int scanMinY,
                                                     int scanMaxY,
                                                     int scanMinZ,
                                                     int scanMaxZ) {
        Set<Long> visited = new LinkedHashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start.immutable());
        visited.add(start.asLong());

        int minX = start.getX();
        int minY = start.getY();
        int minZ = start.getZ();
        int maxX = start.getX();
        int maxY = start.getY();
        int maxZ = start.getZ();

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            minX = Math.min(minX, current.getX());
            minY = Math.min(minY, current.getY());
            minZ = Math.min(minZ, current.getZ());
            maxX = Math.max(maxX, current.getX());
            maxY = Math.max(maxY, current.getY());
            maxZ = Math.max(maxZ, current.getZ());

            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction);
                if (!withinScan(next, scanMinX, scanMaxX, scanMinY, scanMaxY, scanMinZ, scanMaxZ)) {
                    continue;
                }
                if (!isWater(level, next)) {
                    continue;
                }
                if (visited.add(next.asLong())) {
                    queue.add(next.immutable());
                }
            }
        }

        return new WaterComponent(Collections.unmodifiableSet(new LinkedHashSet<>(visited)), minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static FacilityResult collectFacilities(ServerLevel level, WaterComponent component) {
        Set<BlockPos> netPositions = new LinkedHashSet<>();
        Set<BlockPos> bucketPositions = new LinkedHashSet<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int x = component.minX() - 1; x <= component.maxX() + 1; x++) {
            for (int y = component.minY() - 1; y <= component.maxY() + 1; y++) {
                for (int z = component.minZ() - 1; z <= component.maxZ() + 1; z++) {
                    cursor.set(x, y, z);
                    BlockState state = level.getBlockState(cursor);
                    if (state.is(ModBlocks.FISH_NET.get())) {
                        netPositions.add(cursor.immutable());
                        continue;
                    }
                    if (state.is(ModBlocks.FISH_POND_BUCKET.get())) {
                        bucketPositions.add(cursor.immutable());
                    }
                }
            }
        }

        return new FacilityResult(netPositions, bucketPositions);
    }

    private static boolean isWater(ServerLevel level, BlockPos pos) {
        return level.getFluidState(pos).is(Fluids.WATER);
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

    public record ValidationResult(boolean ok, ScanResult scan, Component message) {
    }

    public record ScanResult(Set<Long> waterCells,
                             Set<BlockPos> netPositions,
                             Set<BlockPos> bucketPositions,
                             int minX,
                             int minY,
                             int minZ,
                             int maxX,
                             int maxY,
                             int maxZ) {
        public int width() {
            return maxX - minX + 1;
        }

        public int length() {
            return maxZ - minZ + 1;
        }

        public boolean intersectsEnvelope(FishPondRecord record) {
            return record.intersectsEnvelope(minX, minY, minZ, maxX, maxY, maxZ);
        }
    }

    private record WaterComponent(Set<Long> waterCells,
                                  int minX,
                                  int minY,
                                  int minZ,
                                  int maxX,
                                  int maxY,
                                  int maxZ) {
    }

    private record FacilityResult(Set<BlockPos> netPositions, Set<BlockPos> bucketPositions) {
    }
}
