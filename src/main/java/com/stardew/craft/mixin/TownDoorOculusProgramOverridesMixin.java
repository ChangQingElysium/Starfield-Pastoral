package com.stardew.craft.mixin;

import com.stardew.craft.client.interior.TownDoorRenderContext;
import com.stardew.craft.client.interior.TownDoorOculusPrograms;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Preserve the outer cache and route child views through their own Oculus terrain programs. */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.compat.sodium.impl.shader_overrides.IrisChunkProgramOverrides", remap = false)
public abstract class TownDoorOculusProgramOverridesMixin {
    @Inject(method = "getProgramOverride", at = @At("HEAD"), cancellable = true, require = 1)
    private void stardewcraft$nestedPrograms(@Coerce Object pass, @Coerce Object vertexType, CallbackInfoReturnable<Object> callback) {
        if (!TownDoorRenderContext.isRendering()) return;
        if (!TownDoorRenderContext.usesIsolatedTarget()) callback.setReturnValue(null);
        else if (!TownDoorOculusPrograms.isDelegating()) callback.setReturnValue(TownDoorOculusPrograms.program(pass, vertexType));
    }
}
