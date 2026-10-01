package com.stardew.craft.block.mine;

import com.stardew.craft.block.decor.MapDecorStaticBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;

/** Two-cell recessed iron window, attached to a solid wall, with a shared outline. */
public final class MineIronWindowBlock extends MapDecorStaticBlock {
    public MineIronWindowBlock(Properties properties) {
        super(properties, "block/mine_iron_window", 1, 0, 10, 15, 28, 16);
    }

    @Override public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        Direction facing = context.getClickedFace().getAxis().isHorizontal()
                ? context.getClickedFace() : context.getHorizontalDirection().getOpposite();
        BlockState state = defaultBlockState().setValue(FACING, facing);
        return canPlaceAtFacing(context.getLevel(), context.getClickedPos(), facing, context)
                && state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(PART) == Part.EXTENSION) return super.canSurvive(state, level, pos);
        Direction facing = state.getValue(FACING);
        BlockPos wall = pos.relative(facing.getOpposite());
        return level.getBlockState(wall).isFaceSturdy(level, wall, facing)
                && level.getBlockState(wall.above()).isFaceSturdy(level, wall.above(), facing);
    }

    @Override public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        level.scheduleTick(pos, this, 1);
        return state;
    }

    @Override public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (!level.isClientSide) level.scheduleTick(pos, this, 1);
    }

    @Override public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.canSurvive(level, pos)) runWithDropsSuppressed(() -> level.removeBlock(pos, false));
    }
}
