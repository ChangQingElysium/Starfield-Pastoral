package com.stardew.craft.block.terrain;

import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;

/** The source road's 47-neighborhood topology, isolated from future paving families. */
public final class AsphaltRoadConnections {
    private static final int[][] OFFSETS = {
            {0, -1}, {1, 0}, {0, 1}, {-1, 0},
            {1, -1}, {1, 1}, {-1, 1}, {-1, -1}
    };
    private static final int[] MASKS = java.util.stream.IntStream.range(0, 256)
            .filter(mask -> canonical(mask) == mask)
            .toArray();

    private AsphaltRoadConnections() {
    }

    /** A diagonal contributes only when both of its cardinal rails connect. */
    public static int canonical(int mask) {
        for (int i = 0; i < 4; i++) {
            int adjacent = (1 << i) | (1 << ((i + 1) % 4));
            if ((mask & adjacent) != adjacent) {
                mask &= ~(1 << (i + 4));
            }
        }
        return mask & 255;
    }

    public static int mask(BlockGetter level, BlockPos pos) {
        int result = 0;
        for (int i = 0; i < OFFSETS.length; i++) {
            if (level.getBlockState(pos.offset(OFFSETS[i][0], 0, OFFSETS[i][1])).getBlock()
                    instanceof AsphaltRoadBlock) {
                result |= 1 << i;
            }
        }
        return canonical(result);
    }

    public static int row(int mask) {
        return Arrays.binarySearch(MASKS, canonical(mask));
    }
}
