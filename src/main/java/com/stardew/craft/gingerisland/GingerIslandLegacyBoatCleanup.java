package com.stardew.craft.gingerisland;

import com.google.gson.JsonParser;
import com.stardew.craft.block.decor.MapDecorStaticBlock;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Clears recorded old boat cells only when their MAIN is actually removed. */
final class GingerIslandLegacyBoatCleanup {
    private static final String RESOURCE = "/data/stardewcraft/ginger_island/legacy_boat_footprints.json";
    private static final Map<String, List<BlockPos>> LEGACY = load();

    private GingerIslandLegacyBoatCleanup() {}

    /** Returns true when this method owns MAIN removal, including client BE cleanup. */
    static boolean onMainRemoved(BlockState state, Level level, BlockPos main,
                                 BlockState replacement, boolean moving) {
        if (moving || state.is(replacement.getBlock())
                || state.getValue(MapDecorStaticBlock.PART) != MapDecorStaticBlock.Part.MAIN) return false;
        if (level.isClientSide) return true;
        var block = (MapDecorStaticBlock) state.getBlock();
        Direction facing = state.getValue(MapDecorStaticBlock.FACING);
        Set<BlockPos> offsets = offsets(block, facing);
        Set<BlockPos> protectedCells = protectedByOtherMains(level, block, main, facing, offsets);
        MapDecorStaticBlock.runWithDropsSuppressed(() -> {
            for (BlockPos offset : offsets) {
                BlockPos cell = main.offset(offset);
                if (cell.equals(main) || protectedCells.contains(cell) || !level.hasChunkAt(cell)) continue;
                BlockState existing = level.getBlockState(cell);
                if (!existing.is(block) || existing.getValue(MapDecorStaticBlock.PART) != MapDecorStaticBlock.Part.EXTENSION
                        || existing.getValue(MapDecorStaticBlock.FACING) != facing
                        || level.getBlockEntity(cell) != null) continue;
                // Keep the source water each extension actually replaced. No
                // neighbor cascade should destroy an adjacent legacy assembly.
                level.setBlock(cell, existing.getFluidState().createLegacyBlock(), 2 | 16);
            }
        });
        return true;
    }

    private static Set<BlockPos> offsets(MapDecorStaticBlock block, Direction facing) {
        Set<BlockPos> result = new LinkedHashSet<>(block.placementPositions(BlockPos.ZERO, facing));
        String id = BuiltInRegistries.BLOCK.getKey(block).getPath();
        for (BlockPos offset : LEGACY.getOrDefault(id, List.of())) result.add(rotate(offset, facing));
        return result;
    }

    private static BlockPos rotate(BlockPos offset, Direction facing) {
        return switch (facing) {
            case EAST -> new BlockPos(-offset.getZ(), offset.getY(), offset.getX());
            case SOUTH -> new BlockPos(-offset.getX(), offset.getY(), -offset.getZ());
            case WEST -> new BlockPos(offset.getZ(), offset.getY(), -offset.getX());
            default -> offset;
        };
    }

    private static Set<BlockPos> protectedByOtherMains(Level level, MapDecorStaticBlock block,
                                                      BlockPos removed, Direction facing, Set<BlockPos> offsets) {
        int minX = 0, minY = 0, minZ = 0, maxX = 0, maxY = 0, maxZ = 0;
        for (BlockPos p : offsets) {
            minX = Math.min(minX, p.getX()); maxX = Math.max(maxX, p.getX());
            minY = Math.min(minY, p.getY()); maxY = Math.max(maxY, p.getY());
            minZ = Math.min(minZ, p.getZ()); maxZ = Math.max(maxZ, p.getZ());
        }
        // Only a MAIN inside the difference of the two known footprints can
        // own one of these cells. Query loaded chunks once per column, rather
        // than comparing every old extension with every possible offset.
        Set<BlockPos> result = new HashSet<>();
        BlockPos.MutableBlockPos candidate = new BlockPos.MutableBlockPos();
        for (int x = minX - maxX; x <= maxX - minX; x++) {
            for (int z = minZ - maxZ; z <= maxZ - minZ; z++) {
                candidate.set(removed.getX() + x, removed.getY(), removed.getZ() + z);
                if (!level.hasChunkAt(candidate)) continue;
                for (int y = minY - maxY; y <= maxY - minY; y++) {
                    candidate.setY(removed.getY() + y);
                    if (candidate.equals(removed) || level.isOutsideBuildHeight(candidate)) continue;
                    BlockState other = level.getBlockState(candidate);
                    if (!other.is(block) || other.getValue(MapDecorStaticBlock.PART) != MapDecorStaticBlock.Part.MAIN
                            || other.getValue(MapDecorStaticBlock.FACING) != facing) continue;
                    BlockPos owner = candidate.immutable();
                    for (BlockPos offset : offsets) result.add(owner.offset(offset));
                }
            }
        }
        return result;
    }

    private static Map<String, List<BlockPos>> load() {
        try (var stream = GingerIslandLegacyBoatCleanup.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) throw new IllegalStateException("Missing legacy boat footprint resource");
            var root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.get("version").getAsInt() != 1) throw new IllegalStateException("Unknown boat footprint version");
            Map<String, List<BlockPos>> result = new HashMap<>();
            for (var entry : root.getAsJsonObject("boats").entrySet()) {
                List<BlockPos> cells = new ArrayList<>();
                for (var value : entry.getValue().getAsJsonObject().getAsJsonArray("north_offsets")) {
                    var cell = value.getAsJsonArray();
                    if (cell.size() != 3) throw new IllegalStateException("Invalid legacy boat cell");
                    cells.add(new BlockPos(cell.get(0).getAsInt(), cell.get(1).getAsInt(), cell.get(2).getAsInt()));
                }
                if (cells.size() != new HashSet<>(cells).size() || !cells.contains(BlockPos.ZERO))
                    throw new IllegalStateException("Duplicate/incomplete legacy boat footprint");
                result.put(entry.getKey(), List.copyOf(cells));
            }
            return Map.copyOf(result);
        } catch (Exception error) {
            throw new IllegalStateException("Cannot load the legacy Ginger Island boat footprints", error);
        }
    }
}
