package com.stardew.craft.mixin;

import com.stardew.craft.client.interior.TownDoorRenderContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Oculus caches outer terrain shaders even while the manager uses a vanilla nested pipeline. */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.compat.sodium.impl.shader_overrides.IrisChunkProgramOverrides", remap = false)
public abstract class TownDoorOculusProgramOverridesMixin {
    @Inject(method = "getProgramOverride", at = @At("HEAD"), cancellable = true, require = 1)
    private void stardewcraft$useNestedVanillaShader(CallbackInfoReturnable<Object> callback) {
        if (TownDoorRenderContext.isRendering()) callback.setReturnValue(null);
    }
}
