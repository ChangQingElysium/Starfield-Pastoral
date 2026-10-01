package com.stardew.craft.block.mine;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Flat mine track, using vanilla rail routing with automatic timber end stops. */
@SuppressWarnings("null")
public final class MineRailBlock extends RailBlock {
    public static final BooleanProperty END_NORTH = BooleanProperty.create("end_north");
    public static final BooleanProperty END_SOUTH = BooleanProperty.create("end_south");
    private static final VoxelShape OUTLINE = Block.box(0, 0, 0, 16, 4, 16);

    public MineRailBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(END_NORTH, true).setValue(END_SOUTH, true));
    }

    @Override public boolean canMakeSlopes(BlockState state, BlockGetter level, BlockPos pos) { return false; }
    @Override public boolean isFlexibleRail(BlockState state, BlockGetter level, BlockPos pos) { return false; }
    @Override public boolean isValidRailShape(RailShape shape) { return shape == RailShape.NORTH_SOUTH || shape == RailShape.EAST_WEST; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(END_NORTH, END_SOUTH);
    }

    public static boolean exits(RailShape shape, Direction direction) {
        return switch (shape) {
            case NORTH_SOUTH -> direction == Direction.NORTH || direction == Direction.SOUTH;
            case EAST_WEST -> direction == Direction.EAST || direction == Direction.WEST;
            case NORTH_EAST -> direction == Direction.NORTH || direction == Direction.EAST;
            case NORTH_WEST -> direction == Direction.NORTH || direction == Direction.WEST;
            case SOUTH_EAST -> direction == Direction.SOUTH || direction == Direction.EAST;
            case SOUTH_WEST -> direction == Direction.SOUTH || direction == Direction.WEST;
            default -> false;
        };
    }

    private boolean connected(BlockGetter level, BlockPos pos, Direction direction) {
        BlockPos target = pos.relative(direction);
        BlockState neighbor = level.getBlockState(target);
        return MineRailCurveBlock.opens(neighbor, direction.getOpposite()) || neighbor.getBlock() instanceof BaseRailBlock rail
                && exits(rail.getRailDirection(neighbor, level, target, null), direction.getOpposite());
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (!level.isClientSide) level.scheduleTick(pos, this, 1);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from, boolean moving) {
        super.neighborChanged(state, level, pos, block, from, moving);
        if (!level.isClientSide) level.scheduleTick(pos, this, 1);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        level.scheduleTick(pos, this, 1);
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockState routed = updateDir(level, pos, state, false);
        RailShape shape = routed.getValue(SHAPE);
        boolean straight = shape == RailShape.NORTH_SOUTH || shape == RailShape.EAST_WEST;
        Direction first = shape == RailShape.EAST_WEST ? Direction.EAST : Direction.NORTH;
        BlockState result = routed.setValue(END_NORTH, straight && !connected(level, pos, first))
                .setValue(END_SOUTH, straight && !connected(level, pos, first.getOpposite()));
        if (result != level.getBlockState(pos)) level.setBlock(pos, result, Block.UPDATE_ALL);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return OUTLINE;
    }
}
