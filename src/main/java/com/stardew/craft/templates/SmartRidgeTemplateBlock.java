package com.stardew.craft.templates;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** A four-way ridge network: isolated, end, line, corner, T, and cross are automatic. */
public final class SmartRidgeTemplateBlock extends RoofTemplateBlock {
    public static final BooleanProperty NORTH = BooleanProperty.create("north");
    public static final BooleanProperty EAST = BooleanProperty.create("east");
    public static final BooleanProperty SOUTH = BooleanProperty.create("south");
    public static final BooleanProperty WEST = BooleanProperty.create("west");

    public SmartRidgeTemplateBlock(TemplateShape templateShape, BlockBehaviour.Properties properties) {
        super(templateShape, properties);
        registerDefaultState(defaultBlockState()
                .setValue(NORTH, false)
                .setValue(EAST, false)
                .setValue(SOUTH, false)
                .setValue(WEST, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(NORTH, EAST, SOUTH, WEST);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return state == null ? null : withConnections(state, context.getLevel(), context.getClickedPos());
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        BooleanProperty property = property(direction);
        if (property != null) {
            return state.setValue(property, connects(neighborState, state));
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    private static BlockState withConnections(BlockState state, LevelAccessor level, BlockPos pos) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            state = state.setValue(property(direction), connects(level.getBlockState(pos.relative(direction)), state));
        }
        return state;
    }

    public static int connectionMask(BlockState state) {
        if (!state.hasProperty(NORTH)) return 0;
        return (state.getValue(NORTH) ? 1 : 0) | (state.getValue(EAST) ? 2 : 0)
                | (state.getValue(SOUTH) ? 4 : 0) | (state.getValue(WEST) ? 8 : 0);
    }

    @Override
    public BlockState rotate(BlockState state, net.minecraft.world.level.block.Rotation rotation) {
        BlockState result = super.rotate(state, rotation);
        for (Direction direction : Direction.Plane.HORIZONTAL)
            result = result.setValue(property(rotation.rotate(direction)), state.getValue(property(direction)));
        return result;
    }

    @Override
    public BlockState mirror(BlockState state, net.minecraft.world.level.block.Mirror mirror) {
        BlockState result = super.mirror(state, mirror);
        for (Direction direction : Direction.Plane.HORIZONTAL)
            result = result.setValue(property(mirror.mirror(direction)), state.getValue(property(direction)));
        return result;
    }

    private static boolean connects(BlockState candidate, BlockState state) {
        return candidate.getBlock() == state.getBlock();
    }

    @Nullable
    private static BooleanProperty property(Direction direction) {
        return switch (direction) {
            case NORTH -> NORTH;
            case EAST -> EAST;
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            default -> null;
        };
    }
}
