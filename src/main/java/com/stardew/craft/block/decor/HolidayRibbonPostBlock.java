package com.stardew.craft.block.decor;

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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nonnull;

@SuppressWarnings("null")
public class HolidayRibbonPostBlock extends Block {
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;

    private static final VoxelShape POST_SHAPE = Shapes.or(
            Block.box(0.5D, 0.0D, 0.5D, 15.5D, 2.0D, 15.5D),
            Block.box(5.5D, 2.0D, 5.5D, 10.5D, 20.0D, 10.5D)
    );
    private static final VoxelShape NORTH_ARM = Block.box(7.0D, 0.0D, 0.0D, 9.0D, 18.0D, 8.0D);
    private static final VoxelShape EAST_ARM = Block.box(8.0D, 0.0D, 7.0D, 16.0D, 18.0D, 9.0D);
    private static final VoxelShape SOUTH_ARM = Block.box(7.0D, 0.0D, 8.0D, 9.0D, 18.0D, 16.0D);
    private static final VoxelShape WEST_ARM = Block.box(0.0D, 0.0D, 7.0D, 8.0D, 18.0D, 9.0D);
    private static final VoxelShape[] SHAPES = buildShapes();
    private static final VoxelShape COLLISION_POST_SHAPE = Block.box(5.5D, 0.0D, 5.5D, 10.5D, 24.0D, 10.5D);
    private static final VoxelShape COLLISION_NORTH_ARM = Block.box(7.0D, 0.0D, 0.0D, 9.0D, 24.0D, 8.0D);
    private static final VoxelShape COLLISION_EAST_ARM = Block.box(8.0D, 0.0D, 7.0D, 16.0D, 24.0D, 9.0D);
    private static final VoxelShape COLLISION_SOUTH_ARM = Block.box(7.0D, 0.0D, 8.0D, 9.0D, 24.0D, 16.0D);
    private static final VoxelShape COLLISION_WEST_ARM = Block.box(0.0D, 0.0D, 7.0D, 8.0D, 24.0D, 9.0D);
    private static final VoxelShape[] COLLISION_SHAPES = buildCollisionShapes();

    public HolidayRibbonPostBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(NORTH, false)
                .setValue(EAST, false)
                .setValue(SOUTH, false)
                .setValue(WEST, false));
    }

    @Override
    protected void createBlockStateDefinition(@Nonnull StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST);
    }

    @Override
    public BlockState getStateForPlacement(@Nonnull BlockPlaceContext context) {
        return updateConnections(defaultBlockState(), context.getLevel(), context.getClickedPos());
    }

    @Override
    public BlockState updateShape(@Nonnull BlockState state, @Nonnull Direction direction, @Nonnull BlockState neighborState,
                                     @Nonnull LevelAccessor level, @Nonnull BlockPos pos, @Nonnull BlockPos neighborPos) {
        if (!direction.getAxis().isHorizontal()) {
            return state;
        }
        return state.setValue(propertyFor(direction), connectsTo(neighborState));
    }

    @Override
    public VoxelShape getShape(@Nonnull BlockState state, @Nonnull BlockGetter level, @Nonnull BlockPos pos, @Nonnull CollisionContext context) {
        return SHAPES[index(state)];
    }

    @Override
    public VoxelShape getCollisionShape(@Nonnull BlockState state, @Nonnull BlockGetter level, @Nonnull BlockPos pos, @Nonnull CollisionContext context) {
        return COLLISION_SHAPES[index(state)];
    }

    @Override
    public boolean propagatesSkylightDown(@Nonnull BlockState state, @Nonnull BlockGetter level, @Nonnull BlockPos pos) {
        return true;
    }

    @Override
    public int getLightBlock(@Nonnull BlockState state, @Nonnull BlockGetter level, @Nonnull BlockPos pos) {
        return 0;
    }

    @Override
    public float getShadeBrightness(@Nonnull BlockState state, @Nonnull BlockGetter level, @Nonnull BlockPos pos) {
        return 1.0F;
    }

    @Override
    public BlockState rotate(@Nonnull BlockState state, @Nonnull Rotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_180 -> state
                    .setValue(NORTH, state.getValue(SOUTH))
                    .setValue(EAST, state.getValue(WEST))
                    .setValue(SOUTH, state.getValue(NORTH))
                    .setValue(WEST, state.getValue(EAST));
            case COUNTERCLOCKWISE_90 -> state
                    .setValue(NORTH, state.getValue(EAST))
                    .setValue(EAST, state.getValue(SOUTH))
                    .setValue(SOUTH, state.getValue(WEST))
                    .setValue(WEST, state.getValue(NORTH));
            case CLOCKWISE_90 -> state
                    .setValue(NORTH, state.getValue(WEST))
                    .setValue(EAST, state.getValue(NORTH))
                    .setValue(SOUTH, state.getValue(EAST))
                    .setValue(WEST, state.getValue(SOUTH));
            default -> state;
        };
    }

    @Override
    public BlockState mirror(@Nonnull BlockState state, @Nonnull Mirror mirror) {
        return switch (mirror) {
            case LEFT_RIGHT -> state
                    .setValue(NORTH, state.getValue(SOUTH))
                    .setValue(SOUTH, state.getValue(NORTH));
            case FRONT_BACK -> state
                    .setValue(EAST, state.getValue(WEST))
                    .setValue(WEST, state.getValue(EAST));
            default -> state;
        };
    }

    private BlockState updateConnections(BlockState state, BlockGetter level, BlockPos pos) {
        return state
                .setValue(NORTH, connectsTo(level.getBlockState(pos.north())))
                .setValue(EAST, connectsTo(level.getBlockState(pos.east())))
                .setValue(SOUTH, connectsTo(level.getBlockState(pos.south())))
                .setValue(WEST, connectsTo(level.getBlockState(pos.west())));
    }

    private boolean connectsTo(BlockState state) {
        return state.getBlock() instanceof HolidayRibbonPostBlock;
    }

    private static BooleanProperty propertyFor(Direction direction) {
        return switch (direction) {
            case NORTH -> NORTH;
            case EAST -> EAST;
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            default -> throw new IllegalArgumentException("Not a horizontal direction: " + direction);
        };
    }

    private static VoxelShape[] buildShapes() {
        VoxelShape[] shapes = new VoxelShape[16];
        for (int mask = 0; mask < shapes.length; mask++) {
            boolean north = (mask & 1) != 0;
            boolean east = (mask & 2) != 0;
            boolean south = (mask & 4) != 0;
            boolean west = (mask & 8) != 0;
            boolean straight = (north && south && !east && !west) || (east && west && !north && !south);
            VoxelShape shape = straight ? Shapes.empty() : POST_SHAPE;
            if (north) shape = Shapes.or(shape, NORTH_ARM);
            if (east) shape = Shapes.or(shape, EAST_ARM);
            if (south) shape = Shapes.or(shape, SOUTH_ARM);
            if (west) shape = Shapes.or(shape, WEST_ARM);
            shapes[mask] = shape.optimize();
        }
        return shapes;
    }

    private static VoxelShape[] buildCollisionShapes() {
        VoxelShape[] shapes = new VoxelShape[16];
        for (int mask = 0; mask < shapes.length; mask++) {
            boolean north = (mask & 1) != 0;
            boolean east = (mask & 2) != 0;
            boolean south = (mask & 4) != 0;
            boolean west = (mask & 8) != 0;
            boolean straight = (north && south && !east && !west) || (east && west && !north && !south);
            VoxelShape shape = straight ? Shapes.empty() : COLLISION_POST_SHAPE;
            if (north) shape = Shapes.or(shape, COLLISION_NORTH_ARM);
            if (east) shape = Shapes.or(shape, COLLISION_EAST_ARM);
            if (south) shape = Shapes.or(shape, COLLISION_SOUTH_ARM);
            if (west) shape = Shapes.or(shape, COLLISION_WEST_ARM);
            shapes[mask] = shape.optimize();
        }
        return shapes;
    }

    private static int index(BlockState state) {
        int index = 0;
        if (state.getValue(NORTH)) index |= 1;
        if (state.getValue(EAST)) index |= 2;
        if (state.getValue(SOUTH)) index |= 4;
        if (state.getValue(WEST)) index |= 8;
        return index;
    }
}