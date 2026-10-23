package com.stardew.craft.platform.mixin.client.event.mixin;


import com.stardew.craft.platform.fabric.client.event.FabricClientRegistrationEvents;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(TextureAtlas.class)
public abstract class TextureAtlasUploadMixin {
    @Inject(method = "upload", at = @At("RETURN"))
    private void stardewcraft$atlasUploaded(SpriteLoader.Preparations preparations, CallbackInfo callback) {
        FabricClientRegistrationEvents.onAtlasUploaded((TextureAtlas) (Object) this);
    }
}
