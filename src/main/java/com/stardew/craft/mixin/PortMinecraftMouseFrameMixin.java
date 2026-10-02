package com.stardew.craft.mixin;

import com.stardew.craft.port.PortMouseFrame;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * PORT(1.20.1): once per frame, after the client ticks and before rendering, 1.21.1 {@code Minecraft#runTick} calls
 * {@code MouseHandler#handleAccumulatedMovement}. 1.20.1 calls {@code turnPlayer()} at that point; the 1.21.1 screen
 * movement dispatch for StardewCraft screens runs right before it (see {@code PortMouseHandlerScreenInputMixin}).
 */
@Mixin(Minecraft.class)
public abstract class PortMinecraftMouseFrameMixin {
    @Inject(method = "runTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/MouseHandler;turnPlayer()V"))
    private void port$handleAccumulatedMovement(boolean renderLevel, CallbackInfo ci) {
        ((PortMouseFrame) ((Minecraft) (Object) this).mouseHandler).port$handleAccumulatedMovement();
    }
}
