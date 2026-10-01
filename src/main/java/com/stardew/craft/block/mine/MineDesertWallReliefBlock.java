package com.stardew.craft.block.mine;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.shapes.*;
import javax.annotation.Nullable;

/** One complete three-cell wall ornament, with a shared selection outline and automatic upper parts. */
@SuppressWarnings("null")
public final class MineDesertWallReliefBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty TIER = IntegerProperty.create("tier", 0, 2);
    private static final VoxelShape WHOLE = box(3, 3, 13, 13, 47, 16);

    public MineDesertWallReliefBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(TIER, 0));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, TIER);
    }
    @Override @Nullable public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        if (pos.getY() + 2 >= context.getLevel().getMaxBuildHeight()) return null;
        for (int i = 1; i < 3; i++) if (!context.getLevel().getBlockState(pos.above(i)).canBeReplaced(context)) return null;
        Direction facing = context.getClickedFace().getAxis().isHorizontal()
                ? context.getClickedFace() : context.getHorizontalDirection().getOpposite();
        for (int tier = 0; tier < 3; tier++) {
            BlockPos backing = pos.above(tier).relative(facing.getOpposite());
            if (!context.getLevel().getBlockState(backing).isFaceSturdy(context.getLevel(), backing, facing)) return null;
        }
        BlockState state = defaultBlockState().setValue(FACING, facing);
        return state.canSurvive(context.getLevel(), pos) ? state : null;
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide) for (int i = 1; i < 3; i++) level.setBlock(pos.above(i), state.setValue(TIER, i), 3);
    }
    private boolean matches(BlockState a, BlockState b) {
        return b.is(this) && a.getValue(FACING) == b.getValue(FACING);
    }
    @Override public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        int tier = state.getValue(TIER);
        Direction facing = state.getValue(FACING);
        BlockPos backing = pos.relative(facing.getOpposite());
        if (!level.getBlockState(backing).isFaceSturdy(level, backing, facing)) return false;
        if (tier == 0) return true;
        BlockState root = level.getBlockState(pos.below(tier));
        return matches(state, root) && root.getValue(TIER) == 0;
    }
    @Override public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        level.scheduleTick(pos, this, 1);
        return state;
    }
    @Override public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (!level.isClientSide) level.scheduleTick(pos, this, 1);
    }
    @Override public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.canSurvive(level, pos)) level.removeBlock(pos, false);
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!replacement.is(this) && !level.isClientSide) {
            BlockPos root = pos.below(state.getValue(TIER));
            for (int i = 0; i < 3; i++) {
                BlockPos q = root.above(i);
                if (q.equals(pos)) continue;
                BlockState part = level.getBlockState(q);
                if (matches(state, part) && part.getValue(TIER) == i) level.setBlock(q, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        super.onRemove(state, level, pos, replacement, moving);
    }
    @Override public ItemStack getCloneItemStack(net.minecraft.world.level.BlockGetter level, BlockPos pos, BlockState state) {
        return new ItemStack(this);
    }
    @Override public BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override public BlockState mirror(BlockState state, Mirror mirror) { return state.rotate(mirror.getRotation(state.getValue(FACING))); }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int turns = switch (state.getValue(FACING)) {
            case EAST -> 1; case SOUTH -> 2; case WEST -> 3; default -> 0;
        };
        return com.stardew.craft.block.shape.ModelVoxelShapeCache.rotateY(WHOLE, turns)
                .move(0, -state.getValue(TIER), 0);
    }
    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.join(getShape(state, level, pos, context), Shapes.block(), BooleanOp.AND);
    }
}
