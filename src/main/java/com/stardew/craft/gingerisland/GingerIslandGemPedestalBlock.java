package com.stardew.craft.gingerisland;

import com.stardew.craft.blockentity.GingerIslandGemPedestalBlockEntity;
import com.stardew.craft.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import com.stardew.craft.port.net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

/** A normal empty stand; its gem model reflects one actual stored offering. */
public final class GingerIslandGemPedestalBlock extends GingerIslandStateDecorBlock implements EntityBlock {
    public GingerIslandGemPedestalBlock(Properties properties, GingerIslandAssets.BlockAsset asset) {
        super(properties, asset);
        registerDefaultState(defaultBlockState().setValue(GEM, Gem.EMPTY));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(GEM);
    }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == Part.MAIN ? new GingerIslandGemPedestalBlockEntity(pos, state) : null;
    }

    public static Gem gemFor(ItemStack stack) {
        if (stack.isEmpty()) return Gem.EMPTY;
        if (stack.is(ModItems.AMETHYST.get())) return Gem.AMETHYST;
        if (stack.is(ModItems.AQUAMARINE.get())) return Gem.AQUAMARINE;
        if (stack.is(ModItems.EMERALD.get())) return Gem.EMERALD;
        if (stack.is(ModItems.RUBY.get())) return Gem.RUBY;
        if (stack.is(ModItems.TOPAZ.get())) return Gem.TOPAZ;
        return Gem.EMPTY;
    }

    @Override public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                                        BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (state.getValue(PART) == Part.EXTENSION) return super.useItemOn(stack, state, level, pos, player, hand, hit);
        if (player instanceof ServerPlayer actor && !IslandContext.canModifyAt(actor, pos)) return ItemInteractionResult.FAIL;
        if (!(level.getBlockEntity(pos) instanceof GingerIslandGemPedestalBlockEntity pedestal))
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        // As in ItemPedestal: clicking an occupied stand retrieves its offering,
        // and never replaces it or consumes the new item in the player's hand.
        if (!pedestal.offering().isEmpty()) {
            if (!level.isClientSide) retrieve(level, pos, player, pedestal);
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (gemFor(stack) == Gem.EMPTY) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!level.isClientSide && pedestal.storeOffering(stack)) {
            if (!player.getAbilities().instabuild) stack.shrink(1);
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, .7F, 1);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                         Player player, BlockHitResult hit) {
        if (state.getValue(PART) == Part.EXTENSION) return super.useWithoutItem(state, level, pos, player, hit);
        if (player instanceof ServerPlayer actor && !IslandContext.canModifyAt(actor, pos)) return InteractionResult.FAIL;
        if (!(level.getBlockEntity(pos) instanceof GingerIslandGemPedestalBlockEntity pedestal)
                || pedestal.offering().isEmpty()) return InteractionResult.PASS;
        if (!level.isClientSide) retrieve(level, pos, player, pedestal);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static void retrieve(Level level, BlockPos pos, Player player, GingerIslandGemPedestalBlockEntity pedestal) {
        ItemStack offering = pedestal.offering();
        // One item either fits completely or stays on the stand. No overflow debris.
        // Inventory.add reports success while discarding overflow in creative mode.
        // Check capacity first so a paid offering cannot disappear in a full bag.
        if (player.getInventory().getFreeSlot() < 0
                && player.getInventory().getSlotWithRemainingSpace(offering) < 0) return;
        if (player.getInventory().add(offering)) {
            pedestal.clearOffering();
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, .7F, 1);
        }
    }

    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && state.getValue(PART) == Part.MAIN && !level.isClientSide
                && level.getBlockEntity(pos) instanceof GingerIslandGemPedestalBlockEntity pedestal) {
            ItemStack offering = pedestal.clearOffering();
            if (!dropsSuppressed()) popResource(level, pos, offering);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
