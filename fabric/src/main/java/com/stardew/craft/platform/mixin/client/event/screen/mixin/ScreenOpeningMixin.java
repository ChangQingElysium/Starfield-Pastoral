package com.stardew.craft.platform.mixin.client.event.screen.mixin;


import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.stardew.craft.platform.fabric.client.event.screen.FabricScreenEvents;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(Minecraft.class)
public abstract class ScreenOpeningMixin {
    @Shadow public Screen screen;
    // Native order is ALOAD 0, ALOAD 1, PUTFIELD screen. Run before either operand is on
    // the stack so a replacement local and an early cancellation are both effective.
    @Inject(method = "setScreen", at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD, ordinal = 0,
            target = "Lnet/minecraft/client/Minecraft;screen:Lnet/minecraft/client/gui/screens/Screen;", shift = At.Shift.BY, by = -2), cancellable = true)
    private void stardewcraft$opening(Screen requested, CallbackInfo callback, @Local(argsOnly = true) LocalRef<Screen> target) {
        var transition = FabricScreenEvents.transition(FabricScreenEvents.bus(), screen, target.get());
        if (transition.canceled()) callback.cancel(); else target.set(transition.target());
    }
    @WrapOperation(method = "setScreen", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;removed()V"))
    private void stardewcraft$deferRemoved(Screen old, Operation<Void> original) {
        // Vanilla removes before its title/death fallback. transition() performs the real removal
        // once, after Opening confirms the final destination, matching Neo's reordered lifecycle.
    }
}
