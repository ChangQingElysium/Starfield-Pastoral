package com.stardew.craft.platform.fabric.event.entity.player;

import com.stardew.craft.platform.fabric.event.FabricEvents;
import com.stardew.craft.platform.fabric.util.TriState;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Shared decisions for client prediction and authoritative server actions. */
public final class InteractionDispatch {
    private static final ThreadLocal<Deque<PlayerInteractEvent.LeftClickBlock>> LEFT = ThreadLocal.withInitial(ArrayDeque::new);

    private InteractionDispatch() {}

    public static InteractionResult useBlock(Player player, Level level, ItemStack stack, InteractionHand hand,
            BlockHitResult hit, boolean spectator, boolean creative) {
        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (!level.isClientSide && !state.getBlock().isEnabled(level.enabledFeatures())) return InteractionResult.FAIL;
        var event = FabricEvents.GAME.post(new PlayerInteractEvent.RightClickBlock(player, hand, pos, hit));
        if (event.isCanceled()) return event.getCancellationResult();
        if (spectator) {
            if (level.isClientSide) return InteractionResult.SUCCESS;
            MenuProvider menu = state.getMenuProvider(level, pos);
            if (menu == null) return InteractionResult.PASS;
            player.openMenu(menu);
            return InteractionResult.SUCCESS;
        }
        UseOnContext context = new UseOnContext(player, hand, hit);
        InteractionResult first = executeFirstUse(event, () -> stack.getItem() instanceof ItemUseFirstProvider provider
                ? provider.onItemUseFirst(stack, context) : InteractionResult.PASS);
        if (first != InteractionResult.PASS) return first;
        ItemStack before = stack.copy();
        return executeBlockUse(event,
                () -> !player.isSecondaryUseActive()
                        || player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty(),
                () -> {
                    if (!state.getBlock().isEnabled(level.enabledFeatures())) return InteractionResult.FAIL;
                    ItemInteractionResult blockResult = state.useItemOn(player.getItemInHand(hand), level, player, hand, hit);
                    if (blockResult.consumesAction()) {
                        if (player instanceof ServerPlayer serverPlayer) CriteriaTriggers.ITEM_USED_ON_BLOCK.trigger(serverPlayer, pos, before);
                        return blockResult.result();
                    }
                    if (blockResult == ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION && hand == InteractionHand.MAIN_HAND) {
                        InteractionResult result = state.useWithoutItem(level, player, hit);
                        if (result.consumesAction()) {
                            if (player instanceof ServerPlayer serverPlayer) CriteriaTriggers.DEFAULT_BLOCK_USE.trigger(serverPlayer, pos);
                            return result;
                        }
                    }
                    return InteractionResult.PASS;
                },
                () -> !stack.isEmpty() && !player.getCooldowns().isOnCooldown(stack.getItem()),
                () -> {
                    int count = stack.getCount();
                    InteractionResult result = stack.useOn(context);
                    if (creative) stack.setCount(count);
                    if (result.consumesAction() && player instanceof ServerPlayer serverPlayer) {
                        CriteriaTriggers.ITEM_USED_ON_BLOCK.trigger(serverPlayer, pos, before);
                    }
                    return result;
                });
    }

    /** Cancellation, including a PASS cancellation, never calls the underlying actions. */
    public static InteractionResult executeFirstUse(PlayerInteractEvent.RightClickBlock event,
            Supplier<InteractionResult> firstUse) {
        if (event.isCanceled()) return event.getCancellationResult();
        return event.getUseItem() == TriState.FALSE ? InteractionResult.PASS : firstUse.get();
    }

    public static InteractionResult executeBlockUse(PlayerInteractEvent.RightClickBlock event,
            BooleanSupplier defaultBlock, Supplier<InteractionResult> blockAction,
            BooleanSupplier defaultItem, Supplier<InteractionResult> itemAction) {
        if (event.isCanceled()) return event.getCancellationResult();
        if (allows(event.getUseBlock(), defaultBlock)) {
            InteractionResult result = blockAction.get();
            if (result != InteractionResult.PASS) return result;
        }
        return allows(event.getUseItem(), defaultItem) ? itemAction.get() : InteractionResult.PASS;
    }

    private static boolean allows(TriState decision, BooleanSupplier vanilla) {
        return decision == TriState.TRUE || decision == TriState.DEFAULT && vanilla.getAsBoolean();
    }

    public static <T> T withLeftClick(PlayerInteractEvent.LeftClickBlock event, Supplier<T> action) {
        Deque<PlayerInteractEvent.LeftClickBlock> stack = LEFT.get();
        stack.push(event);
        try { return action.get(); }
        finally { stack.pop(); if (stack.isEmpty()) LEFT.remove(); }
    }

    public static PlayerInteractEvent.LeftClickBlock currentLeftClick() {
        return LEFT.get().peek();
    }
}
