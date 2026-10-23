package com.stardew.craft.platform.mixin.client.event.screen.mixin;


import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stardew.craft.platform.fabric.client.event.screen.FabricScreenEvents;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(KeyboardHandler.class)
public abstract class ScreenKeyboardEventsMixin {
    @WrapOperation(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;keyPressed(III)Z"))
    private static boolean stardewcraft$press(Screen screen, int key, int scan, int modifiers, Operation<Boolean> original) {
        return FabricScreenEvents.key(FabricScreenEvents.bus(), screen, key, scan, modifiers, true, () -> original.call(screen, key, scan, modifiers));
    }
    @WrapOperation(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;keyReleased(III)Z"))
    private static boolean stardewcraft$release(Screen screen, int key, int scan, int modifiers, Operation<Boolean> original) {
        return FabricScreenEvents.key(FabricScreenEvents.bus(), screen, key, scan, modifiers, false, () -> original.call(screen, key, scan, modifiers));
    }
}
