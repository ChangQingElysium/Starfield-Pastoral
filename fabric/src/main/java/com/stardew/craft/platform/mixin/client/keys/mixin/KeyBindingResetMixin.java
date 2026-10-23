package com.stardew.craft.platform.mixin.client.keys.mixin;


import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.InputConstants;
import com.stardew.craft.platform.fabric.client.keys.FabricKeyMappings;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(targets = "net.minecraft.client.gui.screens.options.controls.KeyBindsList$KeyEntry")
public abstract class KeyBindingResetMixin {
    @WrapOperation(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;setKey(Lnet/minecraft/client/KeyMapping;Lcom/mojang/blaze3d/platform/InputConstants$Key;)V"))
    private void stardewcraft$resetModifier(Options options, KeyMapping mapping, InputConstants.Key key, Operation<Void> original) {
        FabricKeyMappings.setToDefault(mapping); original.call(options, mapping, key);
    }
}
