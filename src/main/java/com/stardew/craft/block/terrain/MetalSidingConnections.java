package com.stardew.craft.block.terrain;

import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Eight neighbours in the native UV plane of each of the six cube faces. */
public final class MetalSidingConnections {
    private MetalSidingConnections() {}

    /** Four native 16px quadrants form one 32px sheet; UV-negative axes need the far corner. */
    public static int variant(BlockPos pos, Direction face) {
        var frame = TerrainFaceConnections.frame(face);
        return phase(pos, frame.u()) | (phase(pos, frame.v()) << 1);
    }

    private static int phase(BlockPos pos, Direction direction) {
        int coordinate = direction.getAxis().choose(pos.getX(), pos.getY(), pos.getZ());
        if (direction.getAxisDirection() == Direction.AxisDirection.NEGATIVE) coordinate = -coordinate - 1;
        return Math.floorMod(coordinate, 2);
    }

    public static int mask(Function<BlockPos, BlockState> states, BlockPos pos, Block block, Direction face) {
        int mask = 0;
        for (int edge = 0; edge < 4; edge++) {
            BlockPos neighbor = pos.relative(TerrainFaceConnections.tangent(face, edge));
            if (connects(states, neighbor, block, face)) mask |= 1 << edge;
            BlockPos diagonal = neighbor.relative(TerrainFaceConnections.tangent(face, (edge + 1) % 4));
            if (connects(states, diagonal, block, face)) mask |= 16 << edge;
        }
        return canonical(mask);
    }

    private static boolean connects(Function<BlockPos, BlockState> states, BlockPos pos, Block block, Direction face) {
        // Keep the trim where the flat sheet turns into an inside wall corner.
        return states.apply(pos).is(block) && !states.apply(pos.relative(face)).is(block);
    }

    public static int canonical(int mask) {
        int result = mask & 255;
        for (int corner = 0; corner < 4; corner++) {
            int edges = (1 << corner) | (1 << ((corner + 1) % 4));
            if ((mask & edges) != edges) result &= ~(16 << corner);
        }
        return result;
    }
}
