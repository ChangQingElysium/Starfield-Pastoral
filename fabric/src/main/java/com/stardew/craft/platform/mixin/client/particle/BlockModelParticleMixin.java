package com.stardew.craft.platform.mixin.client.particle;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stardew.craft.platform.fabric.client.model.BakedModels;
import com.stardew.craft.platform.fabric.client.model.ExtendedBakedModel;
import io.github.fabricators_of_create.porting_lib.models.data.ModelData;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BlockModelShaper.class)
public abstract class BlockModelParticleMixin {
    @WrapOperation(method = "getParticleIcon", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/resources/model/BakedModel;getParticleIcon()Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;"), require = 1, allow = 1)
    private TextureAtlasSprite stardewcraft$emptyModelData(BakedModel model, Operation<TextureAtlasSprite> original) {
        return model instanceof ExtendedBakedModel ? BakedModels.getParticleIcon(model, ModelData.EMPTY) : original.call(model);
    }
}
