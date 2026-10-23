package com.stardew.craft.platform.mixin.client.keys.mixin;


import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.platform.InputConstants;
import com.stardew.craft.platform.fabric.client.keys.FabricKeyMappings;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(Options.class)
public abstract class OptionsKeyModifierMixin {
    @WrapOperation(method = "processOptions", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;saveString()Ljava/lang/String;"))
    private String stardewcraft$saveModifier(KeyMapping mapping, Operation<String> original) {
        return FabricKeyMappings.serialize(mapping, original.call(mapping));
    }
    @WrapOperation(method = "processOptions", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/InputConstants;getKey(Ljava/lang/String;)Lcom/mojang/blaze3d/platform/InputConstants$Key;"))
    private InputConstants.Key stardewcraft$loadModifier(String saved, Operation<InputConstants.Key> original, @Local KeyMapping mapping) {
        return FabricKeyMappings.deserialize(mapping, saved, keyName -> original.call(keyName));
    }
}
