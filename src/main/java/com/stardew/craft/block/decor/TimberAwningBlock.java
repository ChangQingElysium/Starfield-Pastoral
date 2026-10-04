package com.stardew.craft.block.decor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** A wall-mounted surround: eight occupied cells, with a genuinely empty 2x2 window. */
public final class TimberAwningBlock extends MapDecorWallStaticBlock {
    public static final IntegerProperty CELL = IntegerProperty.create("cell", 0, 7);
    // NORTH model coordinates, relative to the upper-right model cell (viewer's upper-left).
    private static final BlockPos[] CELLS = {
            BlockPos.ZERO, new BlockPos(-3, -2, 0), new BlockPos(-3, -1, 0),
            new BlockPos(-3, 0, 0), new BlockPos(-2, 0, 0), new BlockPos(-1, 0, 0),
            new BlockPos(0, -2, 0), new BlockPos(0, -1, 0)
    };
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(-46, 0, 1, 14, 10, 16),
            Block.box(-44, -32, 12, -36, 6, 16),
            Block.box(4, -32, 12, 12, 6, 16)).optimize();

    public TimberAwningBlock(Properties properties) {
        super(properties, "stardewcraft:block/decor/house/timber_awning");
        registerDefaultState(defaultBlockState().setValue(CELL, 0));
    }

    @Override
    protected void createBlockStateDefinition(@Nonnull StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(CELL);
    }

    @Override
    protected VoxelShape canonicalShape() { return SHAPE; }

    private static BlockPos offset(int cell, Direction facing) {
        BlockPos p = CELLS[cell];
        return switch (facing) {
            case EAST -> new BlockPos(-p.getZ(), p.getY(), p.getX());
            case SOUTH -> new BlockPos(-p.getX(), p.getY(), -p.getZ());
            case WEST -> new BlockPos(p.getZ(), p.getY(), -p.getX());
            default -> p;
        };
    }

    @Override
    protected BlockState extensionState(BlockState mainState, BlockPos offset) {
        for (int i = 1; i < CELLS.length; i++) {
            if (offset(i, mainState.getValue(FACING)).equals(offset)) {
                return mainState.setValue(PART, Part.EXTENSION).setValue(CELL, i);
            }
        }
        throw new IllegalArgumentException("Not an awning cell: " + offset);
    }

    @Override
    @Nullable
    protected CellOffset findOffsetForExtension(BlockGetter level, BlockPos pos, BlockState state) {
        int cell = state.getValue(CELL);
        if (cell == 0) return null;
        Direction facing = state.getValue(FACING);
        BlockPos offset = offset(cell, facing);
        BlockState main = level.getBlockState(pos.subtract(offset));
        return main.is(this) && main.getValue(PART) == Part.MAIN && main.getValue(CELL) == 0
                && main.getValue(FACING) == facing
                ? new CellOffset(offset.getX(), offset.getY(), offset.getZ()) : null;
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(@Nonnull BlockPlaceContext context) {
        if (!context.getClickedFace().getAxis().isHorizontal()) return null;
        return super.getStateForPlacement(context);
    }

    @Override
    protected boolean canSurvive(@Nonnull BlockState state, @Nonnull LevelReader level, @Nonnull BlockPos pos) {
        if (!super.canSurvive(state, level, pos)) return false;
        BlockPos main = findMainPos(level, pos, state);
        if (main == null) return false;
        Direction facing = state.getValue(FACING);
        for (int i = 0; i < CELLS.length; i++) {
            BlockPos wall = main.offset(offset(i, facing)).relative(facing.getOpposite());
            if (!level.getBlockState(wall).isFaceSturdy(level, wall, facing)) return false;
        }
        return true;
    }

    @Override
    public VoxelShape getShape(@Nonnull BlockState state, @Nonnull BlockGetter level,
                               @Nonnull BlockPos pos, @Nonnull CollisionContext context) {
        return Shapes.join(super.getShape(state, level, pos, context), Shapes.block(), BooleanOp.AND);
    }
}
