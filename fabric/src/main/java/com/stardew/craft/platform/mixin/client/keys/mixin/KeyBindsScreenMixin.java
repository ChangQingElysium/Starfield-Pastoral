package com.stardew.craft.platform.mixin.client.keys.mixin;


import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.InputConstants;
import com.stardew.craft.platform.fabric.client.keys.FabricKeyMappings;
import com.stardew.craft.platform.fabric.client.keys.KeyBindingCapture;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsList;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(KeyBindsScreen.class)
public abstract class KeyBindsScreenMixin extends OptionsSubScreen {
    @Shadow public KeyMapping selectedKey;
    @Shadow public long lastKeySelection;
    @Shadow private KeyBindsList keyBindsList;
    @Unique private final KeyBindingCapture stardewcraft$capture = new KeyBindingCapture();
    protected KeyBindsScreenMixin(Screen parent, Options options, Component title) { super(parent, options, title); }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void stardewcraft$capturePress(int code, int scan, int modifiers, CallbackInfoReturnable<Boolean> callback) {
        if (selectedKey != null) {
            stardewcraft$capture.press(InputConstants.getKey(code, scan), Minecraft.ON_OSX); callback.setReturnValue(true);
        }
    }
    @Override public boolean keyReleased(int code, int scan, int modifiers) {
        if (selectedKey == null || Minecraft.ON_OSX && scan == 63) return super.keyReleased(code, scan, modifiers);
        var result = stardewcraft$capture.release(code, scan, Minecraft.ON_OSX);
        if (result.isPresent()) {
            var binding = result.orElseThrow();
            FabricKeyMappings.setKeyModifierAndCode(selectedKey, binding.modifier(), binding.key());
            options.setKey(selectedKey, binding.key());
            selectedKey = null; lastKeySelection = Util.getMillis(); keyBindsList.resetMappingAndUpdateButtons();
        }
        return true;
    }
    @Inject(method = "mouseClicked", at = @At("RETURN"))
    private void stardewcraft$resetCaptureAfterMouse(double x, double y, int button, CallbackInfoReturnable<Boolean> callback) {
        if (selectedKey == null) stardewcraft$capture.clear();
    }
    @WrapOperation(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;setKey(Lcom/mojang/blaze3d/platform/InputConstants$Key;)V"))
    private void stardewcraft$resetAllModifiers(KeyMapping mapping, InputConstants.Key key, Operation<Void> original) {
        FabricKeyMappings.setToDefault(mapping); original.call(mapping, key);
    }
}
