package com.stardew.craft.platform.mixin.client.event.registration.mixin;


import com.stardew.craft.platform.fabric.client.event.registration.FabricRenderRegistrations;
import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class DimensionEffectsRegistrationMixin {
    @Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Window;updateVsync(Z)V"))
    private void stardewcraft$dimensions(GameConfig config, CallbackInfo callback) { FabricRenderRegistrations.dimensions(); }
}
