package com.stardew.craft.block.decor;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Reserves the lower cell too; the shared decor implementation handles one-item removal. */
public final class CeilingPendantBlock extends MapDecorStaticBlock {
    public CeilingPendantBlock(Properties properties, String model, double x0, double y0, double z0,
                               double x1, double y1, double z1) {
        super(properties, model, x0, y0, z0, x1, y1, z1);
    }

    @Override @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        if (context.getClickedFace() != Direction.DOWN) return null;
        BlockState state = super.getStateForPlacement(context);
        return state != null && state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (!super.canSurvive(state, level, pos)) return false;
        BlockPos main = state.getValue(PART) == Part.MAIN ? pos : findMainPos(level, pos, state);
        return main != null && Block.canSupportCenter(level, main.above(), Direction.DOWN);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }
}
