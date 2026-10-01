package com.stardew.craft.block.utility;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** All four stone palettes share the same north/east/south/west connections. */
public class PondStoneBlock extends Block {
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;
    public static final net.minecraft.world.level.block.state.properties.IntegerProperty VARIANT =
            net.minecraft.world.level.block.state.properties.IntegerProperty.create("variant", 0, 3);
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 16, 16);

    public PondStoneBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(NORTH, false).setValue(EAST, false)
                .setValue(SOUTH, false).setValue(WEST, false).setValue(VARIANT, 0));
    }

    private static BooleanProperty connection(Direction direction) {
        return switch (direction) {
            case NORTH -> NORTH;
            case EAST -> EAST;
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            default -> throw new IllegalArgumentException("Vertical stone connection");
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, VARIANT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState().setValue(VARIANT, context.getLevel().random.nextInt(4));
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            state = state.setValue(connection(direction), context.getLevel()
                    .getBlockState(context.getClickedPos().relative(direction)).getBlock() instanceof PondStoneBlock);
        }
        return state;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return direction.getAxis().isHorizontal()
                ? state.setValue(connection(direction), neighbor.getBlock() instanceof PondStoneBlock) : state;
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        BlockState rotated = state;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            rotated = rotated.setValue(connection(rotation.rotate(direction)), state.getValue(connection(direction)));
        }
        return rotated;
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        BlockState mirrored = state;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            mirrored = mirrored.setValue(connection(mirror.mirror(direction)), state.getValue(connection(direction)));
        }
        return mirrored;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public boolean canBeReplaced(BlockState state, Fluid fluid) {
        return false;
    }
}
