package com.stardew.craft.platform.mixin.client.event.input.mixin;


import com.llamalad7.mixinextras.sugar.Local;
import com.stardew.craft.platform.fabric.client.event.input.FabricInputEvents;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(MouseHandler.class)
public abstract class MouseInputEventsMixin {
    @Shadow private boolean isLeftPressed;
    @Shadow private boolean isMiddlePressed;
    @Shadow private boolean isRightPressed;
    @Shadow private double xpos;
    @Shadow private double ypos;

    // After the window/macOS/touchscreen bookkeeping, before screens or key mappings consume it.
    @Inject(method = "onPress", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/client/Minecraft;getOverlay()Lnet/minecraft/client/gui/screens/Overlay;"), cancellable = true)
    private void stardewcraft$mousePre(long window, int button, int action, int modifiers, CallbackInfo callback) {
        if (FabricInputEvents.mousePre(button, action, modifiers)) callback.cancel();
    }

    @Inject(method = "onPress", at = @At("TAIL"))
    private void stardewcraft$mousePost(long window, int button, int action, int modifiers, CallbackInfo callback) {
        FabricInputEvents.mousePost(button, action, modifiers);
    }

    // These are sensitivity/discrete-adjusted deltas. Accumulation has already consumed whole steps,
    // matching the original hook, and screen/overlay/fractional-only paths never reach this point.
    @Inject(method = "onScroll", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;isSpectator()Z"), cancellable = true)
    private void stardewcraft$scroll(long window, double rawX, double rawY, CallbackInfo callback,
                                   @Local(index = 10) double scrollX, @Local(index = 12) double scrollY) {
        if (FabricInputEvents.scroll(scrollX, scrollY, isLeftPressed, isMiddlePressed, isRightPressed, xpos, ypos)) callback.cancel();
    }
}
