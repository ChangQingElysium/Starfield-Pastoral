package com.stardew.craft.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.stardew.craft.client.model.terrain.SurfaceFloorModels;
import net.minecraft.client.resources.model.BakedModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** CTM initializes only visible CTM models after baking; surface floors must not hide them. */
@Pseudo
@Mixin(targets = "team.chisel.ctm.client.util.TextureMetadataHandler", remap = false)
public abstract class CtmModelInitializationMixin {
    @ModifyExpressionValue(
            // PORT(1.20.1): CTM for Forge 1.20.1 receives Forge's ModelEvent.BakingCompleted (same handler shape as CTM 1.21).
            method = "onModelBake(Lnet/minecraftforge/client/event/ModelEvent$BakingCompleted;)V",
            at = @At(value = "INVOKE", target = "Ljava/util/Map$Entry;getValue()Ljava/lang/Object;")
    )
    private Object stardewcraft$initializeSurfaceHost(Object model) {
        return model instanceof BakedModel baked ? SurfaceFloorModels.unwrapSurface(baked) : model;
    }
}
