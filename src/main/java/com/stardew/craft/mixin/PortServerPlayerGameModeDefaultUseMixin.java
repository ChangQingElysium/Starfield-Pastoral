package com.stardew.craft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.stardew.craft.port.PortBlockInteraction;
import com.stardew.craft.port.PortCriteria;
import net.minecraft.advancements.critereon.ItemUsedOnLocationTrigger;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * PORT(1.20.1): 1.21.1 {@code ServerPlayerGameMode#useItemOn} fires {@code CriteriaTriggers.DEFAULT_BLOCK_USE}
 * (player, pos) when {@code useWithoutItem} consumes the click and {@code ITEM_USED_ON_BLOCK} (player, pos, item)
 * when {@code useItemOn} does. 1.20.1 fires {@code ITEM_USED_ON_BLOCK} after every consuming {@code BlockState#use}.
 * For mod blocks the {@code use} bridge ({@link PortBlockInteraction#dispatch}) reports which 1.21 method consumed
 * the click; the first {@code ITEM_USED_ON_BLOCK} call (the block branch) is then replaced by the 1.21.1
 * {@code DEFAULT_BLOCK_USE} trigger. Vanilla blocks never set the flag and keep the 1.20.1 trigger.
 */
@Mixin(ServerPlayerGameMode.class)
public abstract class PortServerPlayerGameModeDefaultUseMixin {
    @WrapOperation(method = "useItemOn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;use(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;"))
    private InteractionResult stardewcraft$recordDefaultUse(BlockState state, Level level, Player player,
            InteractionHand hand, BlockHitResult hit, Operation<InteractionResult> original,
            @Share("defaultBlockUse") LocalBooleanRef defaultBlockUse) {
        boolean previous = PortBlockInteraction.DEFAULT_BLOCK_USE.get();
        PortBlockInteraction.DEFAULT_BLOCK_USE.set(Boolean.FALSE);
        try {
            InteractionResult result = original.call(state, level, player, hand, hit);
            defaultBlockUse.set(PortBlockInteraction.DEFAULT_BLOCK_USE.get());
            return result;
        } finally {
            PortBlockInteraction.DEFAULT_BLOCK_USE.set(previous);
        }
    }

    @WrapOperation(method = "useItemOn", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/advancements/critereon/ItemUsedOnLocationTrigger;trigger(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;)V"))
    private void stardewcraft$defaultBlockUseTrigger(ItemUsedOnLocationTrigger trigger, ServerPlayer player,
            BlockPos pos, ItemStack stack, Operation<Void> original,
            @Share("defaultBlockUse") LocalBooleanRef defaultBlockUse) {
        if (defaultBlockUse.get()) {
            PortCriteria.DEFAULT_BLOCK_USE.trigger(player, pos);
        } else {
            original.call(trigger, player, pos, stack);
        }
    }
}
