package com.stardew.craft.gingerisland;

import com.stardew.craft.block.utility.TapperBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.util.List;

/** One tree attachment, two reserved cells. The upper cell never owns a second production job. */
public final class HeavyTapperBlock extends TapperBlock {
    public static final BooleanProperty UPPER = BooleanProperty.create("upper");
    public HeavyTapperBlock(Properties properties, String model) {
        super(properties, model);
        registerDefaultState(defaultBlockState().setValue(UPPER, false));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(UPPER);
    }
    @Override public int productionMultiplier() { return 2; }
    @Override public boolean isMainPart(BlockState state) { return !state.getValue(UPPER); }
    private static BlockPos main(BlockState state, BlockPos pos) { return state.getValue(UPPER) ? pos.below() : pos; }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos upper = context.getClickedPos().above();
        if (!context.getLevel().getWorldBorder().isWithinBounds(upper)
                || context.getLevel().isOutsideBuildHeight(upper)
                || !context.getLevel().getFluidState(upper).isEmpty()
                || !context.getLevel().getBlockState(upper).canBeReplaced(context)) return null;
        if (context.getPlayer() instanceof net.minecraft.server.level.ServerPlayer player) {
            if (!IslandContext.canModifyAt(player, upper)) return null;
            if (context.getLevel().dimension() == com.stardew.craft.core.ModDimensions.STARDEW_VALLEY
                    && !com.stardew.craft.event.FarmAreaProtectionEvents.canModifyAt(player, upper)) return null;
        }
        return super.getStateForPlacement(context);
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && isMainPart(state)) level.setBlock(pos.above(), state.setValue(UPPER, true), 3);
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isMainPart(state) ? super.newBlockEntity(pos, state) : null;
    }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return isMainPart(state) ? super.getTicker(level, state, type) : null;
    }
    @Override public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        return isMainPart(state) ? super.canSurvive(state, level, pos)
                : below.is(this) && isMainPart(below) && below.getValue(FACING) == state.getValue(FACING);
    }
    @Override public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (!isMainPart(state)) return canSurvive(state, level, pos) ? state : Blocks.AIR.defaultBlockState();
        if (direction == Direction.UP && !neighbor.is(this)) level.scheduleTick(pos, this, 1);
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }
    @Override public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (isMainPart(state) && !level.getBlockState(pos.above()).is(this)) level.destroyBlock(pos, true);
        else super.tick(state, level, pos, random);
    }
    @Override protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        return isMainPart(state) ? super.getDrops(state, params) : List.of();
    }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return super.getShape(state, level, pos, context).move(0, state.getValue(UPPER) ? -1 : 0, 0);
    }
    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        BlockPos base = main(state, pos);
        BlockState mainState = level.getBlockState(base);
        return mainState.is(this) ? super.useItemOn(stack, mainState, level, base, player, hand, hit)
                : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
    @Override public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        BlockPos base = main(state, pos);
        BlockState mainState = level.getBlockState(base);
        return mainState.is(this) ? super.useWithoutItem(mainState, level, base, player, hit) : InteractionResult.PASS;
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock())) {
            if (isMainPart(state)) {
                if (level.getBlockState(pos.above()).is(this)) level.removeBlock(pos.above(), false);
                super.onRemove(state, level, pos, replacement, moving);
            } else super.onRemove(state, level, pos, replacement, moving);
        }
    }
}
