package com.stardew.craft.block.mine;

import com.stardew.craft.block.terrain.TerrainFaceConnections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Mine material order on flat surfaces and wall/floor corners: loose soil > soil > wall. */
public final class MineGroundConnections {
    private static final int[][] OFFSETS = {{0,-1},{1,0},{0,1},{-1,0},{1,-1},{1,1},{-1,1},{-1,-1}};
    private MineGroundConnections() {}

    public static int rank(BlockState state) {
        for (MineGroundMaterial theme : MineGroundMaterial.values()) {
            int rank = theme.rank(state);
            if (rank >= 0) return rank;
        }
        return -1;
    }

    public static MineGroundMaterial theme(BlockState state) {
        for (MineGroundMaterial theme : MineGroundMaterial.values()) if (theme.rank(state) >= 0) return theme;
        return null;
    }

    public static int mask(BlockGetter level, BlockPos pos, BlockState receiver, int material) {
        int rank = rank(receiver);
        if (rank < 0 || rank >= material || !open(level, pos)) return 0;
        MineGroundMaterial theme = theme(receiver);
        int mask = 0;
        for (int i = 0; i < OFFSETS.length; i++) {
            BlockPos neighbor = pos.offset(OFFSETS[i][0], 0, OFFSETS[i][1]);
            if (theme != null && theme.rank(level.getBlockState(neighbor)) == material && open(level, neighbor)) mask |= 1 << i;
        }
        return mask;
    }

    /** Wall faces and folded wall/floor edges; coplanar tops keep their authored masks. */
    public static List<TerrainFaceConnections.Connection> faceConnections(
            BlockGetter level, BlockPos pos, BlockState receiver, Direction face) {
        MineGroundMaterial theme = theme(receiver);
        if (theme == null) return List.of();
        var connections = TerrainFaceConnections.collect(level, pos, receiver, face, theme::rank,
                (p, f) -> openFace(level, p, f));
        return face == Direction.UP
                ? connections.stream().filter(TerrainFaceConnections.Connection::folded).toList()
                : connections;
    }

    private static boolean openFace(BlockGetter level, BlockPos pos, Direction face) {
        BlockPos outside = pos.relative(face);
        BlockState adjacent = level.getBlockState(outside);
        return adjacent.getFluidState().isEmpty() && !adjacent.is(Blocks.SNOW)
                && adjacent.getCollisionShape(level, outside).isEmpty();
    }

    private static boolean open(BlockGetter level, BlockPos pos) {
        return openFace(level, pos, Direction.UP);
    }
}
