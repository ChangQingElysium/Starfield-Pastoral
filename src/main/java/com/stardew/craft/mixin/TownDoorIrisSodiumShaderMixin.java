package com.stardew.craft.mixin;

import com.stardew.craft.client.interior.TownDoorClipping;
import com.stardew.craft.client.interior.TownDoorForegroundUniforms;
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
    @Unique private int[] stardewcraft$clipLocations;
    @Unique private final TownDoorForegroundUniforms.Program stardewcraft$foregroundUniforms = new TownDoorForegroundUniforms.Program();

    @Inject(method = "setupState", at = @At("RETURN"), require = 1)
    private void stardewcraft$uploadClipPlane(CallbackInfo ci) {
        if (stardewcraft$clipLocations == null) {
            stardewcraft$clipLocations = TownDoorClipping.findIrisUniforms();
        }
        TownDoorClipping.uploadIrisUniforms(stardewcraft$clipLocations);
        stardewcraft$foregroundUniforms.applied();
    }
}
