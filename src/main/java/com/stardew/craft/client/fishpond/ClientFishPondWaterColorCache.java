package com.stardew.craft.client.fishpond;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@OnlyIn(Dist.CLIENT)
public final class ClientFishPondWaterColorCache {

    private static final Map<String, Map<Long, Integer>> COLORS_BY_DIMENSION = new LinkedHashMap<>();

    private ClientFishPondWaterColorCache() {
    }

    public static void applySnapshot(String dimensionId, Map<BlockPos, Integer> colors) {
        Set<BlockPos> changedCells = replaceDimension(dimensionId, colors);
        rerenderChangedCells(dimensionId, changedCells);
    }

    public static Set<BlockPos> replaceDimension(String dimensionId, Map<BlockPos, Integer> colors) {
        Map<Long, Integer> previous = COLORS_BY_DIMENSION.get(dimensionId);
        LinkedHashMap<Long, Integer> packed = new LinkedHashMap<>();
        for (Map.Entry<BlockPos, Integer> entry : colors.entrySet()) {
            packed.put(entry.getKey().asLong(), entry.getValue());
        }
        COLORS_BY_DIMENSION.put(dimensionId, packed);

        LinkedHashSet<BlockPos> changedCells = new LinkedHashSet<>();
        if (previous != null) {
            for (Map.Entry<Long, Integer> entry : previous.entrySet()) {
                if (!Objects.equals(entry.getValue(), packed.get(entry.getKey()))) {
                    changedCells.add(BlockPos.of(entry.getKey()));
                }
            }
        }
        for (Map.Entry<Long, Integer> entry : packed.entrySet()) {
            Integer oldColor = previous == null ? null : previous.get(entry.getKey());
            if (!Objects.equals(oldColor, entry.getValue())) {
                changedCells.add(BlockPos.of(entry.getKey()));
            }
        }
        return changedCells;
    }

    private static void rerenderChangedCells(String dimensionId, Set<BlockPos> changedCells) {
        if (changedCells.isEmpty()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Level level = minecraft.level;
        if (level == null || !dimensionId.equals(level.dimension().location().toString())) {
            return;
        }

        Set<BlockPos> rerenderCells = new LinkedHashSet<>();
        for (BlockPos pos : changedCells) {
            rerenderCells.add(pos);
            for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
                rerenderCells.add(pos.relative(direction));
            }
        }

        for (BlockPos pos : rerenderCells) {
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            level.sendBlockUpdated(pos, state, state, 11);
        }
    }

    public static Integer get(BlockAndTintGetter level, BlockPos pos) {
        if (level == null || pos == null) {
            return null;
        }
        Level actualLevel = level instanceof Level directLevel
            ? directLevel
            : Minecraft.getInstance().level;
        if (actualLevel == null) {
            return null;
        }
        Map<Long, Integer> colors = COLORS_BY_DIMENSION.get(actualLevel.dimension().location().toString());
        if (colors == null) {
            return null;
        }

        Integer exactColor = colors.get(pos.asLong());
        if (exactColor != null) {
            return exactColor;
        }

        // Flowing fluid rendering can sample neighboring positions around the
        // actual pond cell. Fall back to the nearest cached pond-water color so
        // animated/flowing fish pond water keeps the same tint.
        Integer nearestColor = null;
        int bestDistanceSq = Integer.MAX_VALUE;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    BlockPos candidatePos = pos.offset(dx, dy, dz);
                    Integer candidateColor = colors.get(candidatePos.asLong());
                    if (candidateColor == null) {
                        continue;
                    }
                    int distanceSq = dx * dx + dy * dy + dz * dz;
                    if (distanceSq < bestDistanceSq) {
                        bestDistanceSq = distanceSq;
                        nearestColor = candidateColor;
                    }
                }
            }
        }
        return nearestColor;
    }

    public static void clearAll() {
        COLORS_BY_DIMENSION.clear();
    }

    public static Map<String, Map<Long, Integer>> view() {
        return Collections.unmodifiableMap(COLORS_BY_DIMENSION);
    }
}