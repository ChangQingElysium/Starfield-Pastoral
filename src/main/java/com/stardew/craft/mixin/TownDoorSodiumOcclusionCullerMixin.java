package com.stardew.craft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stardew.craft.client.interior.TownDoorRenderContext;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Disables Sodium cave occlusion only for the nested doorway view, as Immersive Portals does. */
@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.occlusion.OcclusionCuller", remap = false)
public abstract class TownDoorSodiumOcclusionCullerMixin {
    @ModifyVariable(method = "findVisible", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 1)
    private boolean stardewcraft$keepRemoteGroundVisible(boolean useOcclusionCulling) {
        return TownDoorRenderContext.isRendering() ? false : useOcclusionCulling;
    }

    /**
     * A translated camera sits behind the destination plane. Starting Sodium's graph walk there can
     * make the tight doorway frustum reject the walk before it reaches the actual destination.
     * Immersive Portals solves the same case by moving the iteration origin to the portal aperture.
     */
    @WrapOperation(method = "init", at = @At(value = "INVOKE",
            target = "Lme/jellysquid/mods/sodium/client/render/viewport/Viewport;getChunkCoord()Lnet/minecraft/core/SectionPos;"),
            require = 1, remap = false)
    private SectionPos stardewcraft$startAtDestination(@Coerce Object viewport,
                                                        Operation<SectionPos> original) {
        return stardewcraft$destinationOrOriginal(viewport, original);
    }

    @WrapOperation(method = "initWithinWorld", at = @At(value = "INVOKE",
            target = "Lme/jellysquid/mods/sodium/client/render/viewport/Viewport;getChunkCoord()Lnet/minecraft/core/SectionPos;"),
            require = 1, remap = false)
    private SectionPos stardewcraft$startInsideDestination(@Coerce Object viewport,
                                                            Operation<SectionPos> original) {
        return stardewcraft$destinationOrOriginal(viewport, original);
    }

    private static SectionPos stardewcraft$destinationOrOriginal(Object viewport,
                                                                  Operation<SectionPos> original) {
        SectionPos origin = TownDoorRenderContext.discoveryOrigin();
        return origin != null ? origin : original.call(viewport);
    }
}
