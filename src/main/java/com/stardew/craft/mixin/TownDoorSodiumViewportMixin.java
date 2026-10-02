package com.stardew.craft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stardew.craft.client.interior.TownDoorRenderContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

/** Sodium equivalent of the nested doorway frustum, adapted from Immersive Portals (Apache-2.0). */
@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.viewport.Viewport", remap = false)
public abstract class TownDoorSodiumViewportMixin {
    @WrapOperation(method = "isBoxVisible", at = @At(value = "INVOKE",
            target = "Lme/jellysquid/mods/sodium/client/render/viewport/frustum/Frustum;testAab(FFFFFF)Z"),
            require = 1, remap = false)
    private boolean stardewcraft$cullOutsideDoorway(@Coerce Object frustum,
                                                     float minX, float minY, float minZ,
                                                     float maxX, float maxY, float maxZ,
                                                     Operation<Boolean> original) {
        boolean visible = original.call(frustum, minX, minY, minZ, maxX, maxY, maxZ);
        return visible && !TownDoorRenderContext.isCameraBoxOutsidePortalView(
                minX, minY, minZ, maxX, maxY, maxZ);
    }
}
