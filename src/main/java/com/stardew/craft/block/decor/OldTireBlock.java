package com.stardew.craft.block.decor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** One ground decoration, with a low ring of coarse boxes and an open center. */
public final class OldTireBlock extends MapDecorStaticBlock {
    private static final VoxelShape RING = Shapes.or(
            Block.box(5, 0, 1, 11, 4, 5), Block.box(5, 0, 11, 11, 4, 15),
            Block.box(1, 0, 5, 5, 4, 11), Block.box(11, 0, 5, 15, 4, 11),
            Block.box(2, 0, 2, 5, 4, 5), Block.box(11, 0, 2, 14, 4, 5),
            Block.box(2, 0, 11, 5, 4, 14), Block.box(11, 0, 11, 14, 4, 14));

    public OldTireBlock(Properties properties) {
        super(properties, "stardewcraft:block/decor/old_tire/spring/old_tire_spring");
    }

    @Override protected VoxelShape canonicalShape() { return RING; }

    @Override public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return super.canSurvive(state, level, pos)
                && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }
}
