package com.stardew.craft.mixin;

import com.stardew.craft.client.weapon.ConsumedInteractionClientState;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Prevents a shared right-click binding from also firing a weapon skill after a successful interaction. */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeInteractionMixin {

    @Inject(method = "useItemOn", at = @At("RETURN"))
    private void stardewcraft$trackBlockInteraction(LocalPlayer player,
                                                     InteractionHand hand,
                                                     BlockHitResult hit,
                                                     CallbackInfoReturnable<InteractionResult> cir) {
        markIfConsumed(cir.getReturnValue());
    }

    @Inject(method = "interact", at = @At("RETURN"))
    private void stardewcraft$trackEntityInteraction(Player player,
                                                      Entity entity,
                                                      InteractionHand hand,
                                                      CallbackInfoReturnable<InteractionResult> cir) {
        markIfConsumed(cir.getReturnValue());
    }

    @Inject(method = "interactAt", at = @At("RETURN"))
    private void stardewcraft$trackEntityInteractionAt(Player player,
                                                        Entity entity,
                                                        EntityHitResult hit,
                                                        InteractionHand hand,
                                                        CallbackInfoReturnable<InteractionResult> cir) {
        markIfConsumed(cir.getReturnValue());
    }

    private static void markIfConsumed(InteractionResult result) {
        if (result != null && result.consumesAction()) {
            ConsumedInteractionClientState.markConsumed();
        }
    }
}
