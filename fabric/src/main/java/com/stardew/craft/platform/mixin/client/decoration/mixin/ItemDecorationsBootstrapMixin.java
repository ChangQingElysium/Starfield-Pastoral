package com.stardew.craft.platform.mixin.client.decoration.mixin;


import com.stardew.craft.platform.fabric.client.decoration.FabricItemDecorations;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(Minecraft.class)
public abstract class ItemDecorationsBootstrapMixin {
    @Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Window;updateVsync(Z)V"))
    private void stardewcraft$decorations(GameConfig config, CallbackInfo callback) { FabricItemDecorations.initialize(); }
}
