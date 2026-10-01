package com.stardew.craft.block.mine;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import com.stardew.craft.port.net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import com.stardew.craft.port.net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.shapes.*;
import javax.annotation.Nullable;

/** Shallow stone flakes that join only along the same supported wall plane. */
public final class MineWallFlakesBlock extends Block {
    public static final MapCodec<MineWallFlakesBlock> CODEC = simpleCodec(MineWallFlakesBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty VARIANT = IntegerProperty.create("variant", 0, 2);
    public static final BooleanProperty ABOVE = BooleanProperty.create("above");
    public static final BooleanProperty BELOW = BooleanProperty.create("below");
    public static final BooleanProperty LEFT = BooleanProperty.create("left");
    public static final BooleanProperty RIGHT = BooleanProperty.create("right");

    public MineWallFlakesBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(VARIANT, 0)
                .setValue(ABOVE, false).setValue(BELOW, false).setValue(LEFT, false).setValue(RIGHT, false));
    }

    @Override public MapCodec<MineWallFlakesBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, VARIANT, ABOVE, BELOW, LEFT, RIGHT);
    }

    @Nullable
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        Integer fixed = context.getItemInHand().getOrDefault(DataComponents.BLOCK_STATE,
                BlockItemStateProperties.EMPTY).get(VARIANT);
        int variant = fixed == null ? context.getLevel().getRandom().nextInt(3) : fixed;
        BlockState clicked = context.getLevel().getBlockState(context.getClickedPos().relative(context.getClickedFace().getOpposite()));
        Direction preferred = !context.getClickedFace().getAxis().isHorizontal() && clicked.is(this)
                ? clicked.getValue(FACING) : context.getClickedFace();
        if (preferred.getAxis().isHorizontal()) {
            BlockState state = defaultBlockState().setValue(FACING, preferred).setValue(VARIANT, variant);
            if (state.canSurvive(context.getLevel(), context.getClickedPos())) return connections(state, context.getLevel(), context.getClickedPos());
        }
        for (Direction direction : context.getNearestLookingDirections()) {
            if (!direction.getAxis().isHorizontal()) continue;
            BlockState state = defaultBlockState().setValue(FACING, direction.getOpposite()).setValue(VARIANT, variant);
            if (state.canSurvive(context.getLevel(), context.getClickedPos())) return connections(state, context.getLevel(), context.getClickedPos());
        }
        return null;
    }

    private BlockState connections(BlockState state, BlockGetter level, BlockPos pos) {
        Direction right = state.getValue(FACING).getClockWise();
        return state.setValue(ABOVE, joins(state, level.getBlockState(pos.above())))
                .setValue(BELOW, joins(state, level.getBlockState(pos.below())))
                .setValue(LEFT, joins(state, level.getBlockState(pos.relative(right.getOpposite()))))
                .setValue(RIGHT, joins(state, level.getBlockState(pos.relative(right))));
    }

    private boolean joins(BlockState state, BlockState other) {
        return other.is(this) && other.getValue(FACING) == state.getValue(FACING);
    }

    @Override protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos wall = pos.relative(facing.getOpposite());
        return level.getBlockState(wall).isFaceSturdy(level, wall, facing);
    }

    @Override protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return state.canSurvive(level, pos) ? connections(state, level, pos) : Blocks.AIR.defaultBlockState();
    }

    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        if (!level.isClientSide) level.scheduleTick(pos, this, 1);
    }

    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.canSurvive(level, pos)) level.removeBlock(pos, false);
        else {
            BlockState connected = connections(state, level, pos);
            if (connected != state) level.setBlock(pos, connected, UPDATE_ALL);
        }
    }

    @Override public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        ItemStack stack = new ItemStack(this);
        stack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(VARIANT, state));
        return stack;
    }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> box(0, 0, 0, 16, 16, 2);
            case EAST -> box(0, 0, 0, 2, 16, 16);
            case WEST -> box(14, 0, 0, 16, 16, 16);
            default -> box(0, 0, 14, 16, 16, 16);
        };
    }

    @Override protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override protected BlockState mirror(BlockState state, Mirror mirror) {
        if (mirror == Mirror.NONE) return state;
        return state.rotate(mirror.getRotation(state.getValue(FACING)))
                .setValue(LEFT, state.getValue(RIGHT)).setValue(RIGHT, state.getValue(LEFT));
    }
}
