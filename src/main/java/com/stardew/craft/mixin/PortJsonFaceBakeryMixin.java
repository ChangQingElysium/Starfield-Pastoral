package com.stardew.craft.mixin;

import com.stardew.craft.port.PortFaceBakery;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockElementRotation;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps ordinary mod JSON on the same baker as imported models, without changing other namespaces. */
@Mixin(FaceBakery.class)
public abstract class PortJsonFaceBakeryMixin {
    @Inject(method = "bakeQuad", at = @At("HEAD"), cancellable = true, require = 1)
    private void stardewcraft$bakeJson(Vector3f from, Vector3f to, BlockElementFace face,
            TextureAtlasSprite sprite, Direction facing, ModelState state, BlockElementRotation rotation,
            boolean shade, ResourceLocation modelLocation, CallbackInfoReturnable<BakedQuad> cir) {
        if (modelLocation != null && "stardewcraft".equals(modelLocation.getNamespace())) {
            // PORT(1.20.1): no extra 0.1% UV inset; geometric normals and NeoForge winding rules.
            cir.setReturnValue(PortFaceBakery.bakeQuad(from, to, face, sprite, facing, state, rotation, shade));
        }
    }
}
