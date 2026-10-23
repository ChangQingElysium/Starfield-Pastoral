package com.stardew.craft.platform.mixin.client.event.input.mixin;


import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.stardew.craft.platform.fabric.client.event.InputEvent;
import com.stardew.craft.platform.fabric.client.event.input.FabricInputEvents;
import com.stardew.craft.platform.fabric.client.extension.ClientBlockEffects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(Minecraft.class)
public abstract class InteractionInputEventsMixin {
    @Shadow @Final public Options options;
    @Shadow @Final public ParticleEngine particleEngine;
    @Shadow public LocalPlayer player;

    @Inject(method = "startAttack", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/phys/HitResult;getType()Lnet/minecraft/world/phys/HitResult$Type;"), cancellable = true)
    private void stardewcraft$attack(CallbackInfoReturnable<Boolean> callback,
                                   @Share("stardewcraft$attack") LocalRef<InputEvent.InteractionKeyMappingTriggered> frame) {
        var event = FabricInputEvents.interaction(0, options.keyAttack, InteractionHand.MAIN_HAND);
        frame.set(event);
        if (FabricInputEvents.consumeCanceled(event, () -> {}, () -> player.swing(InteractionHand.MAIN_HAND))) callback.setReturnValue(false);
    }

    @WrapOperation(method = "startAttack", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;swing(Lnet/minecraft/world/InteractionHand;)V"))
    private void stardewcraft$attackSwing(LocalPlayer player, InteractionHand hand, Operation<Void> original,
                                        @Share("stardewcraft$attack") LocalRef<InputEvent.InteractionKeyMappingTriggered> frame) {
        if (FabricInputEvents.shouldSwing(frame.get())) original.call(player, hand);
    }

    @Inject(method = "continueAttack", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/phys/BlockHitResult;getDirection()Lnet/minecraft/core/Direction;"), cancellable = true)
    private void stardewcraft$heldAttack(boolean leftClick, CallbackInfo callback,
                                       @Local BlockHitResult hit, @Local BlockPos pos,
                                       @Share("stardewcraft$heldAttack") LocalRef<InputEvent.InteractionKeyMappingTriggered> frame) {
        var event = FabricInputEvents.interaction(0, options.keyAttack, InteractionHand.MAIN_HAND);
        frame.set(event);
        if (FabricInputEvents.consumeCanceled(event, () -> ClientBlockEffects.crack(particleEngine, pos, hit),
                () -> player.swing(InteractionHand.MAIN_HAND))) callback.cancel();
    }

    @ModifyExpressionValue(method = "continueAttack", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;continueDestroyBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z"))
    private boolean stardewcraft$heldAttackEffects(boolean continuing,
                                                 @Share("stardewcraft$heldAttack") LocalRef<InputEvent.InteractionKeyMappingTriggered> frame) {
        // The actual mining call still ran. Only its visual crack/swing branch is suppressed.
        return continuing && FabricInputEvents.shouldSwing(frame.get());
    }

    @Inject(method = "startUseItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;getItemInHand(Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/item/ItemStack;"), cancellable = true)
    private void stardewcraft$useItem(CallbackInfo callback, @Local InteractionHand hand,
                                    @Share("stardewcraft$useItem") LocalRef<InputEvent.InteractionKeyMappingTriggered> frame) {
        var event = FabricInputEvents.interaction(1, options.keyUse, hand);
        frame.set(event);
        // Deliberately inside the native hand loop. Consumption/failure/cancellation returns before
        // the offhand exactly as in NeoForge's patched source, despite its contrary API javadoc.
        if (FabricInputEvents.consumeCanceled(event, () -> {}, () -> player.swing(hand))) callback.cancel();
    }

    @ModifyExpressionValue(method = "startUseItem", at = {
            @At(value = "INVOKE", target = "Lnet/minecraft/world/InteractionResult;shouldSwing()Z", ordinal = 0),
            @At(value = "INVOKE", target = "Lnet/minecraft/world/InteractionResult;shouldSwing()Z", ordinal = 1)})
    private boolean stardewcraft$useSwing(boolean original,
                                        @Share("stardewcraft$useItem") LocalRef<InputEvent.InteractionKeyMappingTriggered> frame) {
        // Preserve the original entity/block branches, including the block's itemUsed animation.
        // The third (air-use fallback) shouldSwing call is intentionally unmodified: NeoForge does
        // not apply its input flag there either.
        return original && FabricInputEvents.shouldSwing(frame.get());
    }

    @Inject(method = "pickBlock", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/client/player/LocalPlayer;getAbilities()Lnet/minecraft/world/entity/player/Abilities;"), cancellable = true)
    private void stardewcraft$pickBlock(CallbackInfo callback) {
        if (FabricInputEvents.interaction(2, options.keyPickItem, InteractionHand.MAIN_HAND).isCanceled()) callback.cancel();
    }
}
