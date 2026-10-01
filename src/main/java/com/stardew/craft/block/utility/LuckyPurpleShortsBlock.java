package com.stardew.craft.block.utility;

import com.stardew.craft.block.shape.ModelVoxelShapeCache;
import com.stardew.craft.blockentity.LuckyPurpleShortsBlockEntity;
import com.stardew.craft.event.LuckyPurpleShortsWorldEvents;
import com.stardew.craft.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import com.stardew.craft.port.net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.List;
import com.stardew.craft.port.PortBlockInteraction;

public class LuckyPurpleShortsBlock extends Block implements EntityBlock, PortBlockInteraction {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private static final VoxelShape[] SHAPES = ModelVoxelShapeCache.horizontalShapes(
            "stardewcraft:block/utility/lucky_purple_shorts", Direction.NORTH);

    @SuppressWarnings("null")
    public LuckyPurpleShortsBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(@SuppressWarnings("null") StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public RenderShape getRenderShape(@SuppressWarnings("null") BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(@SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") BlockState state) {
        return new LuckyPurpleShortsBlockEntity(pos, state);
    }

    @Override
    public BlockState getStateForPlacement(@SuppressWarnings("null") BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @SuppressWarnings("null")
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[ModelVoxelShapeCache.horizontalIndex(state.getValue(FACING))];
    }

    @SuppressWarnings("null")
    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @SuppressWarnings("null")
    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        return List.of(new ItemStack(ModItems.LUCKY_PURPLE_SHORTS.get()));
    }

    @SuppressWarnings("null")
    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @SuppressWarnings("null")
    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }

    // PORT(1.20.1): replay the 1.21 useItemOn/useWithoutItem dispatch.
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        return PortBlockInteraction.dispatch(this, state, level, pos, player, hand, hit);
    }

    @SuppressWarnings("null")
    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (LuckyPurpleShortsWorldEvents.isSpecialShortsPosition(level, pos)) {
            if (!level.isClientSide) {
                LuckyPurpleShortsWorldEvents.useSpecialShorts(level, pos, player);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return pickUpPlacedShorts(level, pos, player);
    }

    @SuppressWarnings("null")
    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (LuckyPurpleShortsWorldEvents.isSpecialShortsPosition(level, pos)) {
            return LuckyPurpleShortsWorldEvents.useSpecialShorts(level, pos, player);
        }

        ItemInteractionResult result = pickUpPlacedShorts(level, pos, player);
        return result == ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION ? InteractionResult.PASS : InteractionResult.CONSUME;
    }

    private ItemInteractionResult pickUpPlacedShorts(Level level, BlockPos pos, Player player) {
        if (level.isClientSide) {
            return ItemInteractionResult.sidedSuccess(true);
        }

        ItemStack shorts = new ItemStack(ModItems.LUCKY_PURPLE_SHORTS.get());
        if (!player.getInventory().add(shorts)) {
            player.drop(shorts, false);
        }
        level.removeBlock(pos, false);
        return ItemInteractionResult.sidedSuccess(false);
    }
}
