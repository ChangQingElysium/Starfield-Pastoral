package com.stardew.craft.block.decor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** One independently removable planter cell; adjacent cells share an open soil bed. */
public final class GardenPlanterBlock extends Block {
    public static final Direction[] SIDES = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    public static final BooleanProperty[] CONNECTIONS = {BlockStateProperties.NORTH, BlockStateProperties.EAST,
            BlockStateProperties.SOUTH, BlockStateProperties.WEST};
    public static final BooleanProperty[] DIAGONALS = {BooleanProperty.create("north_west"), BooleanProperty.create("north_east"),
            BooleanProperty.create("south_west"), BooleanProperty.create("south_east")};
    private static final int[][] CORNERS = {{0, 3}, {0, 1}, {2, 3}, {2, 1}};
    private static final VoxelShape[] SHAPES = makeShapes();

    public GardenPlanterBlock(Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any();
        for (var property : CONNECTIONS) state = state.setValue(property, false);
        for (var property : DIAGONALS) state = state.setValue(property, false);
        registerDefaultState(state);
    }

    // PORT(1.20.1): NeoForge TriState TRUE -> true; DEFAULT -> Forge's default soil rules (plant's mayPlaceOn first).
    @Override
    public boolean canSustainPlant(BlockState state, BlockGetter level,
            BlockPos soilPosition, Direction facing, net.minecraftforge.common.IPlantable plant) {
        return facing == Direction.UP || super.canSustainPlant(state, level, soilPosition, facing, plant);
    }

    /** Both halves of tall flowers sink together to the open soil surface. */
    public static boolean lowersPlant(BlockGetter level, BlockPos pos, BlockState plant) {
        if (plant.getBlock() instanceof NaturalPlantBlock) return false; // Already lowered by its own model.
        if (!(plant.getBlock() instanceof net.minecraft.world.level.block.BushBlock)
                && !plant.is(net.minecraft.world.level.block.Blocks.SPORE_BLOSSOM)) return false;
        BlockPos root = pos;
        if (plant.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && plant.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF)
                == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER) root = pos.below();
        return level.getBlockState(root.below()).getBlock() instanceof GardenPlanterBlock;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CONNECTIONS).add(DIAGONALS);
    }

    private BlockState connectedState(BlockState state, BlockGetter level, BlockPos pos) {
        for (int i = 0; i < 4; i++) {
            state = state.setValue(CONNECTIONS[i], level.getBlockState(pos.relative(SIDES[i])).is(this));
            state = state.setValue(DIAGONALS[i], level.getBlockState(pos.relative(SIDES[CORNERS[i][0]])
                    .relative(SIDES[CORNERS[i][1]])).is(this));
        }
        return state;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return connectedState(defaultBlockState(), context.getLevel(), context.getClickedPos());
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return connectedState(state, level, pos);
    }

    private void refreshAround(Level level, BlockPos pos) {
        if (level.isClientSide) return;
        // Diagonal changes must refresh concave corners even without a direct face notification.
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            BlockPos at = pos.offset(x, 0, z);
            BlockState state = level.getBlockState(at);
            if (state.is(this)) {
                BlockState next = connectedState(state, level, at);
                if (next != state) level.setBlock(at, next, UPDATE_ALL);
            }
        }
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (!oldState.is(this)) refreshAround(level, pos);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        super.onRemove(state, level, pos, newState, moved);
        if (!newState.is(this)) refreshAround(level, pos);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int mask = 0;
        for (int i = 0; i < 4; i++) {
            if (state.getValue(CONNECTIONS[i])) mask |= 1 << i;
            if (state.getValue(DIAGONALS[i])) mask |= 1 << (i + 4);
        }
        return SHAPES[mask];
    }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) { return false; }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) { return transform(state, rotation::rotate); }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) { return transform(state, mirror::mirror); }

    private BlockState transform(BlockState state, java.util.function.UnaryOperator<Direction> operation) {
        BlockState result = state;
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                if (operation.apply(SIDES[i]) == SIDES[j]) result = result.setValue(CONNECTIONS[j], state.getValue(CONNECTIONS[i]));
                Direction a = operation.apply(SIDES[CORNERS[i][0]]), b = operation.apply(SIDES[CORNERS[i][1]]);
                Direction c = SIDES[CORNERS[j][0]], d = SIDES[CORNERS[j][1]];
                if ((a == c && b == d) || (a == d && b == c)) result = result.setValue(DIAGONALS[j], state.getValue(DIAGONALS[i]));
            }
        }
        return result;
    }

    private static VoxelShape[] makeShapes() {
        VoxelShape[] shapes = new VoxelShape[256];
        VoxelShape[] walls = {box(2, 12, 0, 14, 16, 2), box(14, 12, 2, 16, 16, 14),
                box(2, 12, 14, 14, 16, 16), box(0, 12, 2, 2, 16, 14)};
        for (int mask = 0; mask < 256; mask++) {
            VoxelShape shape = box(0, 5, 0, 16, 12, 16);
            for (int i = 0; i < 4; i++) {
                if ((mask & (1 << i)) == 0) shape = Shapes.or(shape, walls[i]);
                if ((mask & (1 << CORNERS[i][0])) == 0 || (mask & (1 << CORNERS[i][1])) == 0 || (mask & (1 << (i + 4))) == 0) {
                    int x = (i % 2) * 14, z = (i / 2) * 14;
                    shape = Shapes.or(shape, box(x, 0, z, x + 2, 16, z + 2));
                }
            }
            shapes[mask] = shape.optimize();
        }
        return shapes;
    }
}
