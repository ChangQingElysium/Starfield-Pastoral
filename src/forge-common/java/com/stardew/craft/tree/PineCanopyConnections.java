package com.stardew.craft.tree;

import com.stardew.craft.block.ModBlocks;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/** Geometry neighbours and the eight horizontal neighbours of a snow shelf. */
public final class PineCanopyConnections {
    private static final int[][] OFFSETS = {{0,-1},{1,0},{0,1},{-1,0},{1,-1},{1,1},{-1,1},{-1,-1}};

    private PineCanopyConnections() {}

    public record Canopy(int hiddenFaces, int snowMask, int variant, boolean snowExposed) {
        public boolean hidden(Direction face) { return (hiddenFaces & (1 << face.ordinal())) != 0; }
    }

    public static Canopy inspect(Function<BlockPos, BlockState> states, BlockPos pos) {
        int hidden = 0, mask = 0;
        for (Direction direction : Direction.values())
            if (pine(states.apply(pos.relative(direction)))) hidden |= 1 << direction.ordinal();
        for (int i = 0; i < OFFSETS.length; i++)
            if (pine(states.apply(pos.offset(OFFSETS[i][0], 0, OFFSETS[i][1])))) mask |= 1 << i;
        BlockState above = states.apply(pos.above());
        boolean exposed = !pine(above) && !above.canOcclude() && above.getFluidState().isEmpty();
        int variation = Math.floorMod(net.minecraft.util.Mth.getSeed(pos), 3);
        return new Canopy(hidden, canonical(mask), variation, exposed);
    }

    private static boolean pine(BlockState state) { return state.is(ModBlocks.PINE_LEAVES.get()); }

    /** A diagonal only contributes an inner corner when both adjoining edges connect. */
    public static int canonical(int mask) {
        int result = mask & 255;
        for (int corner = 0; corner < 4; corner++) {
            int sides = (1 << corner) | (1 << ((corner + 1) % 4));
            if ((mask & sides) != sides) result &= ~(16 << corner);
        }
        return result;
    }
}
