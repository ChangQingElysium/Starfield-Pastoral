package com.stardew.craft.block.mine;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.stardew.craft.port.net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import com.stardew.craft.port.net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.shapes.*;
import javax.annotation.Nullable;

/** One three-cell landmark, with purple/green variants and a shared selection outline. */
@SuppressWarnings("null")
public final class MineSerpentPillarBlock extends Block {
    public static final MapCodec<MineSerpentPillarBlock> CODEC = simpleCodec(MineSerpentPillarBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty TIER = IntegerProperty.create("tier", 0, 2);
    public static final IntegerProperty VARIANT = IntegerProperty.create("variant", 0, 1);
    private static final VoxelShape WHOLE = box(0, 0, 0, 16, 46, 16);

    public MineSerpentPillarBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(TIER, 0).setValue(VARIANT, 0));
    }
    @Override public MapCodec<MineSerpentPillarBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, TIER, VARIANT);
    }
    @Override @Nullable public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        if (pos.getY() + 2 >= context.getLevel().getMaxBuildHeight()) return null;
        for (int i = 1; i < 3; i++) if (!context.getLevel().getBlockState(pos.above(i)).canBeReplaced(context)) return null;
        Integer variant = context.getItemInHand().getOrDefault(DataComponents.BLOCK_STATE,
                BlockItemStateProperties.EMPTY).get(VARIANT);
        BlockState state = defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(VARIANT, variant == null ? 0 : variant);
        return state.canSurvive(context.getLevel(), pos) ? state : null;
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide) for (int i = 1; i < 3; i++) level.setBlock(pos.above(i), state.setValue(TIER, i), 3);
    }
    private boolean matches(BlockState a, BlockState b) {
        return b.is(this) && a.getValue(FACING) == b.getValue(FACING) && a.getValue(VARIANT).equals(b.getValue(VARIANT));
    }
    @Override protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        int tier = state.getValue(TIER);
        if (tier == 0) return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
        BlockState root = level.getBlockState(pos.below(tier));
        return matches(state, root) && root.getValue(TIER) == 0;
    }
    @Override protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        level.scheduleTick(pos, this, 1);
        return state;
    }
    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (!level.isClientSide) level.scheduleTick(pos, this, 1);
    }
    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.canSurvive(level, pos)) level.removeBlock(pos, false);
    }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
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
    @Override public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        ItemStack stack = new ItemStack(this);
        stack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(VARIANT, state));
        return stack;
    }
    @Override protected BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override protected BlockState mirror(BlockState state, Mirror mirror) { return state.rotate(mirror.getRotation(state.getValue(FACING))); }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return WHOLE.move(0, -state.getValue(TIER), 0);
    }
    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.join(getShape(state, level, pos, context), Shapes.block(), BooleanOp.AND);
    }
}
