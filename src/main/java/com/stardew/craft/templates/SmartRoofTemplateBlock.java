package com.stardew.craft.templates;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.StairsShape;

/** A roof profile that immediately chooses straight, inner, or outer joins. */
public final class SmartRoofTemplateBlock extends RoofTemplateBlock {
    public static final EnumProperty<StairsShape> ROOF_SHAPE = BlockStateProperties.STAIRS_SHAPE;

    public SmartRoofTemplateBlock(TemplateShape templateShape, BlockBehaviour.Properties properties) {
        super(templateShape, properties);
        registerDefaultState(defaultBlockState().setValue(ROOF_SHAPE, StairsShape.STRAIGHT));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ROOF_SHAPE);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return state == null ? null : state.setValue(ROOF_SHAPE,
                calculateShape(state, context.getLevel(), context.getClickedPos()));
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return withShape(state, level, pos);
    }

    static BlockState withShape(BlockState state, BlockGetter level, BlockPos pos) {
        return state.setValue(ROOF_SHAPE, calculateShape(state, level, pos));
    }

    private static StairsShape calculateShape(BlockState state, BlockGetter level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockState front = level.getBlockState(pos.relative(facing));
        if (isCompatible(front, state)) {
            Direction neighborFacing = front.getValue(FACING);
            if (neighborFacing.getAxis() != facing.getAxis()
                    && canTakeShape(state, level, pos, neighborFacing.getOpposite())) {
                return neighborFacing == facing.getCounterClockWise()
                        ? StairsShape.OUTER_LEFT : StairsShape.OUTER_RIGHT;
            }
        }

        BlockState back = level.getBlockState(pos.relative(facing.getOpposite()));
        if (isCompatible(back, state)) {
            Direction neighborFacing = back.getValue(FACING);
            if (neighborFacing.getAxis() != facing.getAxis()
                    && canTakeShape(state, level, pos, neighborFacing)) {
                return neighborFacing == facing.getCounterClockWise()
                        ? StairsShape.INNER_LEFT : StairsShape.INNER_RIGHT;
            }
        }
        // At the next course of a hip roof, the perpendicular supporting
        // slope sits one block lower and sideways, not at the same Y level.
        if (((RoofTemplateBlock)state.getBlock()).templateShape().roofForm() == RoofTemplateForm.SLOPE) {
            Direction left = facing.getCounterClockWise(), right = facing.getClockWise();
            int dy = state.getValue(FLIPPED) ? 1 : -1;
            BlockState leftSupport = level.getBlockState(pos.relative(left.getOpposite()).offset(0, dy, 0));
            BlockState rightSupport = level.getBlockState(pos.relative(right.getOpposite()).offset(0, dy, 0));
            boolean l = isCompatible(leftSupport, state) && leftSupport.getValue(FACING) == left;
            boolean r = isCompatible(rightSupport, state) && rightSupport.getValue(FACING) == right;
            if (l != r) return l ? StairsShape.OUTER_LEFT : StairsShape.OUTER_RIGHT;
        }
        return StairsShape.STRAIGHT;
    }

    private static boolean canTakeShape(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        BlockState sideState = level.getBlockState(pos.relative(side));
        return !isCompatible(sideState, state) || sideState.getValue(FACING) != state.getValue(FACING);
    }

    private static boolean isCompatible(BlockState candidate, BlockState state) {
        return candidate.getBlock() == state.getBlock()
                && candidate.getValue(FLIPPED) == state.getValue(FLIPPED);
    }
}
