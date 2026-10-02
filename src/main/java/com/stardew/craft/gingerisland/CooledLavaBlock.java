package com.stardew.craft.gingerisland;

import com.stardew.craft.block.shape.ModelVoxelShapeCache;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Thin stones on the lava surface, not an obsidian cube. Neighbour bits are N/E/S/W. */
public final class CooledLavaBlock extends Block {
    public static final IntegerProperty NEIGHBORS = IntegerProperty.create("neighbors", 0, 15);

    public CooledLavaBlock(Properties properties) {
        super(properties.dynamicShape());
        registerDefaultState(defaultBlockState().setValue(NEIGHBORS, 0));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(NEIGHBORS); }
    public BlockState connected(LevelAccessor level, BlockPos pos) {
        int mask = 0;
        Direction[] sides = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        for (int i = 0; i < sides.length; i++) if (level.getBlockState(pos.relative(sides[i])).is(this)) mask |= 1 << i;
        return defaultBlockState().setValue(NEIGHBORS, mask);
    }
    @Override public BlockState updateShape(BlockState state, Direction side, BlockState neighbor,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return connected(level, pos);
    }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return ModelVoxelShapeCache.requiredShape("stardewcraft:block/ginger_island/cooled_lava/" + state.getValue(NEIGHBORS));
    }
}
