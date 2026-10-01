package com.stardew.craft.port;

import com.stardew.craft.port.net.minecraft.world.ItemInteractionResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Restores the 1.20.5+ block interaction split on 1.20.1.
 *
 * <p>1.21 calls {@code useItemOn} and, when it returns {@link ItemInteractionResult#PASS_TO_DEFAULT_BLOCK_INTERACTION}
 * for the main hand, {@code useWithoutItem}. 1.20.1 only calls {@code Block#use}. Every mod block that overrides
 * either 1.21 method implements this interface (the topmost such mod class also gets a {@code use} bridge, inserted by
 * {@code scripts/port/adapt_block_use.py}) which replays the 1.21.1 {@code ServerPlayerGameMode#useItemOn} /
 * {@code MultiPlayerGameMode#performUseItemOn} block phase through {@link #dispatch}.
 *
 * <p>Defaults mirror 1.21.1 {@code BlockBehaviour}. The methods are public because interface methods must be; the
 * script widens the overriding declarations from {@code protected}.
 */
public interface PortBlockInteraction {

    default ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hitResult) {
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    default InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        return InteractionResult.PASS;
    }

    /**
     * Body of the generated {@code Block#use} bridge. Same order and conditions as the 1.21.1 block phase:
     * {@code useItemOn} with the stack in {@code hand}; a consuming result ends the interaction with
     * {@link ItemInteractionResult#result()}; {@code PASS_TO_DEFAULT_BLOCK_INTERACTION} on the main hand falls through
     * to {@code useWithoutItem}, whose result ends it only when it consumes the action. Anything else returns
     * {@link InteractionResult#PASS} so the 1.20.1 caller continues with {@code Item#useOn}, exactly like 1.21.
     * The sneak-bypass gate and Forge's {@code RightClickBlock} useBlock result are applied by the 1.20.1 caller
     * around {@code use}, as in 1.21.
     */
    static InteractionResult dispatch(PortBlockInteraction block, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hitResult) {
        ItemInteractionResult itemResult = block.useItemOn(player.getItemInHand(hand), state, level, pos, player, hand,
                hitResult);
        if (itemResult.consumesAction()) {
            return itemResult.result();
        }
        if (itemResult == ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION && hand == InteractionHand.MAIN_HAND) {
            InteractionResult result = block.useWithoutItem(state, level, pos, player, hitResult);
            if (result.consumesAction()) {
                return result;
            }
        }
        return InteractionResult.PASS;
    }

    /** 1.21 {@code BlockStateBase#useItemOn(ItemStack, Level, Player, InteractionHand, BlockHitResult)}. */
    static ItemInteractionResult stateUseItemOn(BlockState state, ItemStack stack, Level level, Player player,
            InteractionHand hand, BlockHitResult hitResult) {
        if (state.getBlock() instanceof PortBlockInteraction block) {
            return block.useItemOn(stack, state, level, hitResult.getBlockPos(), player, hand, hitResult);
        }
        // Vanilla 1.20.1 blocks only have the combined use(); its consuming result stands in for useItemOn.
        InteractionResult result = state.use(level, player, hand, hitResult);
        return switch (result) {
            case SUCCESS -> ItemInteractionResult.SUCCESS;
            case CONSUME -> ItemInteractionResult.CONSUME;
            case CONSUME_PARTIAL -> ItemInteractionResult.CONSUME_PARTIAL;
            case FAIL -> ItemInteractionResult.FAIL;
            case PASS -> ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        };
    }

    /** 1.21 {@code BlockStateBase#useWithoutItem(Level, Player, BlockHitResult)}. */
    static InteractionResult stateUseWithoutItem(BlockState state, Level level, Player player, BlockHitResult hitResult) {
        if (state.getBlock() instanceof PortBlockInteraction block) {
            return block.useWithoutItem(state, level, hitResult.getBlockPos(), player, hitResult);
        }
        return state.use(level, player, InteractionHand.MAIN_HAND, hitResult);
    }
}
