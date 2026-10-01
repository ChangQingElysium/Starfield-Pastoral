package com.stardew.craft.block.decor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@SuppressWarnings("null")
public class MapDecorCeilingStaticBlock extends MapDecorStaticBlock {
    public MapDecorCeilingStaticBlock(Properties properties, String modelId) {
        super(properties, modelId);
    }

    public MapDecorCeilingStaticBlock(Properties properties, String modelId, boolean boxCollision) {
        super(properties, modelId, boxCollision);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(@Nonnull BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        if (state == null) {
            return null;
        }
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    public boolean canSurvive(@Nonnull BlockState state, @Nonnull LevelReader level, @Nonnull BlockPos pos) {
        if (!super.canSurvive(state, level, pos)) {
            return false;
        }
        BlockPos supportPos = pos.above();
        BlockState support = level.getBlockState(supportPos);
        return support.isFaceSturdy(level, supportPos, Direction.DOWN);
    }

    @Override
    public BlockState updateShape(@Nonnull BlockState state,
                                     @Nonnull Direction direction,
                                     @Nonnull BlockState neighborState,
                                     @Nonnull LevelAccessor level,
                                     @Nonnull BlockPos pos,
                                     @Nonnull BlockPos neighborPos) {
        return state.canSurvive(level, pos) ? state : Blocks.AIR.defaultBlockState();
    }
}
