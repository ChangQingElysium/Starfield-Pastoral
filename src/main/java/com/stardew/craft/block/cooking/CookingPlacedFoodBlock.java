package com.stardew.craft.block.cooking;

import com.mojang.serialization.MapCodec;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.blockentity.CookingPlacedFoodBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.stardew.craft.port.net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import com.stardew.craft.port.net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nonnull;

@SuppressWarnings("null")
public class CookingPlacedFoodBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<CookingPlacedFoodBlock> CODEC =
            simpleCodec(properties -> new CookingPlacedFoodBlock("", properties));
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    private static final VoxelShape SHAPE = Block.box(1.0D, 0.0D, 1.0D, 15.0D, 8.0D, 15.0D);

    private final String itemId;

    public CookingPlacedFoodBlock(String itemId, Properties properties) {
        super(properties);
        this.itemId = itemId;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public String getItemId() {
        return itemId;
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return Block.canSupportCenter(level, below, Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == Direction.DOWN && !state.canSurvive(level, pos)) {
            return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    protected boolean canBeReplaced(@Nonnull BlockState state, @Nonnull Fluid fluid) {
        return false;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CookingPlacedFoodBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                             Player player, InteractionHand hand, BlockHitResult hit) {
        if (player == null) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (handleInteraction(level, pos, player)) {
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (handleInteraction(level, pos, player)) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    private boolean handleInteraction(Level level, BlockPos pos, Player player) {
        if (player.isShiftKeyDown()) {
            return pickup(level, pos, player);
        }
        return eat(level, pos, player);
    }

    private boolean pickup(Level level, BlockPos pos, Player player) {
        ItemStack food = createFoodStack(level, pos);
        if (food.isEmpty()) {
            return false;
        }

        if (!level.isClientSide) {
            if (!level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3)) {
                return false;
            }
            if (!player.addItem(food)) {
                player.drop(food, false);
            }
            level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.7F, 1.0F);
        }
        return true;
    }

    private boolean eat(Level level, BlockPos pos, Player player) {
        ItemStack food = createFoodStack(level, pos);
        if (food.isEmpty() || !food.has(DataComponents.FOOD)) {
            return false;
        }

        if (!level.isClientSide) {
            // A protected decoration must be consumed successfully before awarding its effects.
            if (!level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3)) {
                return false;
            }
            food.finishUsingItem(level, player);
        }
        return true;
    }

    private @Nonnull ItemStack createFoodStack(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof CookingPlacedFoodBlockEntity foodEntity) {
            ItemStack stored = foodEntity.getStoredFood();
            if (!stored.isEmpty()) {
                return stored;
            }
        }
        Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(StardewCraft.MODID, itemId));
        if (item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(item);
    }
}
