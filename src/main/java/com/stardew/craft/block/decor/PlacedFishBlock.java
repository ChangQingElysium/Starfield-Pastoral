package com.stardew.craft.block.decor;

import com.mojang.serialization.MapCodec;
import com.stardew.craft.blockentity.PlacedFishBlockEntity;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import com.stardew.craft.port.PortBlockInteraction;

/** The fish is the placed object. Its wall-only cradle has no separate block or item registration. */
public final class PlacedFishBlock extends HorizontalDirectionalBlock implements EntityBlock, PortBlockInteraction {
    public static final BooleanProperty WALL = BooleanProperty.create("wall");
    private static final VoxelShape FLOOR = Block.box(1, 0, 1, 15, 8, 15);
    private static final java.util.Map<Direction, VoxelShape> WALL_SHAPES = java.util.Map.of(
            Direction.NORTH, Block.box(.5, 1, 3, 15.5, 16, 16),
            Direction.SOUTH, Block.box(.5, 1, 0, 15.5, 16, 13),
            Direction.EAST, Block.box(0, 1, .5, 13, 16, 15.5),
            Direction.WEST, Block.box(3, 1, .5, 16, 16, 15.5));

    public PlacedFishBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(WALL, false));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, WALL); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new PlacedFishBlockEntity(pos, state); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        if (face == Direction.DOWN) return null;
        return defaultBlockState().setValue(WALL, face != Direction.UP)
                .setValue(FACING, face == Direction.UP ? context.getHorizontalDirection().getOpposite() : face);
    }
    @Override public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (!state.getValue(WALL)) return Block.canSupportCenter(level, pos.below(), Direction.UP);
        Direction facing = state.getValue(FACING);
        BlockPos support = pos.relative(facing.getOpposite());
        return level.getBlockState(support).isFaceSturdy(level, support, facing);
    }
    @Override public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor,
                                                LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        Direction support = state.getValue(WALL) ? state.getValue(FACING).getOpposite() : Direction.DOWN;
        return direction == support && !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }
    @Override public boolean canBeReplaced(BlockState state, Fluid fluid) { return false; }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(WALL) ? WALL_SHAPES.get(state.getValue(FACING)) : FLOOR;
    }
    @Override public ItemStack getCloneItemStack(BlockState state, HitResult target, BlockGetter level, BlockPos pos, Player player) {
        return level.getBlockEntity(pos) instanceof PlacedFishBlockEntity fish ? fish.fish() : ItemStack.EMPTY;
    }
    // PORT(1.20.1): replay the 1.21 useItemOn/useWithoutItem dispatch.
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        return PortBlockInteraction.dispatch(this, state, level, pos, player, hand, hit);
    }

    @Override public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.getMainHandItem().isEmpty() || !player.mayBuild()) return InteractionResult.PASS;
        if (!(level.getBlockEntity(pos) instanceof PlacedFishBlockEntity fish) || fish.fish().isEmpty()) return InteractionResult.PASS;
        if (!level.isClientSide) {
            ItemStack stack = fish.takeFish();
            level.removeBlock(pos, false);
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            level.playSound(null, pos, ModSounds.DWOP.get(), SoundSource.BLOCKS, .65F, 1.15F);
            level.gameEvent(GameEvent.BLOCK_DESTROY, pos, GameEvent.Context.of(player, state));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!state.is(next.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof PlacedFishBlockEntity fish)
            Block.popResource(level, pos, fish.takeFish());
        super.onRemove(state, level, pos, next, moving);
    }

    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                                      net.minecraft.world.entity.LivingEntity placer, ItemStack stack) {
        if (level.getBlockEntity(pos) instanceof PlacedFishBlockEntity fish) fish.storeFish(stack);
    }
}
