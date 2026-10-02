package com.stardew.craft.mixin;

import com.stardew.craft.client.interior.TownDoorClipping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Iris/Sodium clipping-uniform hook adapted from Immersive Portals (Apache-2.0). */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.compat.sodium.impl.shader_overrides.IrisChunkShaderInterface", remap = false)
public abstract class TownDoorIrisSodiumShaderMixin {
    @Unique private int stardewcraft$clipLocation = Integer.MIN_VALUE;

    @Inject(method = "setupState", at = @At("RETURN"), require = 1)
    private void stardewcraft$uploadClipPlane(CallbackInfo ci) {
        if (stardewcraft$clipLocation == Integer.MIN_VALUE) {
            stardewcraft$clipLocation = TownDoorClipping.findInActiveProgram();
        }
        TownDoorClipping.uploadRendererModUniform(stardewcraft$clipLocation);
    }
}
