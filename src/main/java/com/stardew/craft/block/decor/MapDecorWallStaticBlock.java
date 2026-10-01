package com.stardew.craft.block.decor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@SuppressWarnings("null")
public class MapDecorWallStaticBlock extends MapDecorStaticBlock {

    public MapDecorWallStaticBlock(Properties properties, String modelId) {
        super(properties, modelId);
    }

    public MapDecorWallStaticBlock(Properties properties, String modelId, boolean boxCollision) {
        super(properties, modelId, boxCollision);
    }

    public MapDecorWallStaticBlock(Properties properties, String modelId,
                                   double minX, double minY, double minZ,
                                   double maxX, double maxY, double maxZ) {
        super(properties, modelId, minX, minY, minZ, maxX, maxY, maxZ);
    }

    public MapDecorWallStaticBlock(Properties properties, String modelId, int extensionOffsetX, int extensionOffsetY, int extensionOffsetZ) {
        super(properties, modelId, extensionOffsetX, extensionOffsetY, extensionOffsetZ);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(@Nonnull BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        Direction clicked = context.getClickedFace();
        Direction facing = clicked.getAxis().isHorizontal() ? clicked : context.getHorizontalDirection().getOpposite();
        if (!canPlaceAtFacing(level, pos, facing, context)) return null;
        BlockState state = defaultBlockState().setValue(PART, Part.MAIN).setValue(FACING, facing);
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    protected java.util.Set<CellOffset> occupiedOffsets(Direction facing) {
        java.util.Set<CellOffset> cells = new java.util.LinkedHashSet<>(super.occupiedOffsets(facing));
        cells.removeIf(offset -> isSupportSideOverflow(offset, facing));
        return cells;
    }

    @Override
    public boolean canSurvive(@Nonnull BlockState state, @Nonnull LevelReader level, @Nonnull BlockPos pos) {
        if (!super.canSurvive(state, level, pos)) {
            return false;
        }
        BlockPos mainPos = state.getValue(PART) == Part.MAIN ? pos : findMainPos(level, pos, state);
        if (mainPos == null) {
            return false;
        }
        BlockState mainState = level.getBlockState(mainPos);
        Direction facing = mainState.is(this) && mainState.hasProperty(FACING) ? mainState.getValue(FACING) : state.getValue(FACING);
        BlockPos supportPos = mainPos.relative(facing.getOpposite());
        BlockState support = level.getBlockState(supportPos);
        return support.isFaceSturdy(level, supportPos, facing);
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

    private boolean isSupportSideOverflow(CellOffset offset, Direction facing) {
        return switch (facing) {
            case NORTH -> offset.dz() > 0;
            case SOUTH -> offset.dz() < 0;
            case EAST -> offset.dx() < 0;
            case WEST -> offset.dx() > 0;
            default -> false;
        };
    }

}
