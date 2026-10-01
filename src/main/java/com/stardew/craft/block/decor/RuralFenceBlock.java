package com.stardew.craft.block.decor;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Two occupied cells, one shared post, and optional rails at each horizontal boundary. */
public final class RuralFenceBlock extends MapDecorStaticBlock {
    public static final IntegerProperty VARIANT = IntegerProperty.create("variant", 0, 2);
    public static final Map<Direction, BooleanProperty> CONNECTIONS = Map.of(
            Direction.NORTH, BlockStateProperties.NORTH, Direction.EAST, BlockStateProperties.EAST,
            Direction.SOUTH, BlockStateProperties.SOUTH, Direction.WEST, BlockStateProperties.WEST);
    private static final VoxelShape POST = Shapes.or(
            Block.box(6, 0, 5, 10, 29, 11), Block.box(5, 0, 6, 6, 29, 10),
            Block.box(10, 0, 6, 11, 29, 10), Block.box(6, 29, 6, 10, 31, 10)).optimize();
    private static final VoxelShape[] SHAPES = makeShapes();

    public RuralFenceBlock(Properties properties) {
        this(properties, "stardewcraft:block/decor/rural_fence");
    }

    public RuralFenceBlock(Properties properties, String modelId) {
        super(properties, modelId);
        BlockState state = defaultBlockState().setValue(VARIANT, 0);
        for (BooleanProperty connection : CONNECTIONS.values()) state = state.setValue(connection, false);
        registerDefaultState(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(VARIANT, BlockStateProperties.NORTH, BlockStateProperties.EAST,
                BlockStateProperties.SOUTH, BlockStateProperties.WEST);
    }

    @Override
    protected VoxelShape canonicalShape() {
        // The footprint is always the same two cells, even when there are no rails.
        return POST;
    }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) {
        return false;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return state == null ? null : withConnections(state.setValue(FACING, Direction.NORTH)
                .setValue(VARIANT, context.getLevel().random.nextInt(3)), context.getLevel(), context.getClickedPos());
    }

    private BlockState withConnections(BlockState state, LevelReader level, BlockPos pos) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            state = state.setValue(CONNECTIONS.get(direction), connects(level, pos, direction));
        }
        return state;
    }

    private boolean connects(LevelReader level, BlockPos pos, Direction direction) {
        BlockPos neighborPos = pos.relative(direction);
        BlockState neighbor = level.getBlockState(neighborPos);
        if (neighbor.getBlock() instanceof RuralFenceBlock) return neighbor.getValue(PART) == Part.MAIN;
        BlockState upper = level.getBlockState(neighborPos.above());
        return !isExceptionForConnection(neighbor) && !isExceptionForConnection(upper)
                && neighbor.isFaceSturdy(level, neighborPos, direction.getOpposite())
                && upper.isFaceSturdy(level, neighborPos.above(), direction.getOpposite());
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        BlockState result = super.updateShape(state, direction, neighborState, level, pos, neighborPos);
        if (!result.is(this)) return result;
        if (state.getValue(PART) == Part.MAIN) {
            return direction.getAxis().isHorizontal() ? withConnections(result, level, pos) : result;
        }
        if (direction == Direction.DOWN && neighborState.is(this) && neighborState.getValue(PART) == Part.MAIN) {
            return neighborState.setValue(PART, Part.EXTENSION);
        }
        if (direction.getAxis().isHorizontal()) {
            // A wall's upper cell is adjacent to our extension, not the base.
            level.scheduleTick(pos.below(), this, 1);
        }
        return result;
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(PART) == Part.MAIN) {
            BlockState updated = withConnections(state, level, pos);
            if (updated != state) level.setBlock(pos, updated, UPDATE_ALL);
        }
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        BlockPos mainPos = findMainPos(level, pos, state);
        if (mainPos == null) return Shapes.empty();
        BlockState main = state.getValue(PART) == Part.MAIN ? state : level.getBlockState(mainPos);
        int mask = 0;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (main.getValue(CONNECTIONS.get(direction))) mask |= 1 << direction.get2DDataValue();
        }
        return SHAPES[mask].move(0, mainPos.getY() - pos.getY(), 0);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        BlockState result = super.rotate(state, rotation);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            result = result.setValue(CONNECTIONS.get(rotation.rotate(direction)), state.getValue(CONNECTIONS.get(direction)));
        }
        return result;
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        BlockState result = state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            result = result.setValue(CONNECTIONS.get(mirror.mirror(direction)), state.getValue(CONNECTIONS.get(direction)));
        }
        return result;
    }

    private static VoxelShape[] makeShapes() {
        VoxelShape[] shapes = new VoxelShape[16];
        for (int mask = 0; mask < shapes.length; mask++) {
            VoxelShape shape = POST;
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                if ((mask & (1 << direction.get2DDataValue())) == 0) continue;
                for (int y : new int[]{9, 21}) {
                    VoxelShape rail = switch (direction) {
                        case NORTH -> Block.box(6, y, 0, 10, y + 4, 5);
                        case SOUTH -> Block.box(6, y, 11, 10, y + 4, 16);
                        case WEST -> Block.box(0, y, 6, 5, y + 4, 10);
                        case EAST -> Block.box(11, y, 6, 16, y + 4, 10);
                        default -> Shapes.empty();
                    };
                    shape = Shapes.or(shape, rail);
                }
            }
            shapes[mask] = shape.optimize();
        }
        return shapes;
    }
}
