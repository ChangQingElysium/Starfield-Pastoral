package com.stardew.craft.mixin;

import com.stardew.craft.client.interior.TownDoorClipping;
import com.stardew.craft.client.interior.TownDoorForegroundUniforms;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Both Iris shader implementations override apply, so the vanilla hook cannot upload this plane. */
@Pseudo
@Mixin(targets = {"net.irisshaders.iris.pipeline.programs.ExtendedShader",
        "net.irisshaders.iris.pipeline.programs.FallbackShader"}, remap = false)
public abstract class TownDoorIrisExtendedShaderMixin {
    @Unique private int[] stardewcraft$clipLocations;
    @Unique private final TownDoorForegroundUniforms.Program stardewcraft$foregroundUniforms = new TownDoorForegroundUniforms.Program();

    @Inject(method = {"apply", "m_173363_"}, at = @At("RETURN"), require = 1)
    private void stardewcraft$clipGeometry(CallbackInfo ci) {
        if (stardewcraft$clipLocations == null) stardewcraft$clipLocations = TownDoorClipping.findIrisUniforms();
        TownDoorClipping.uploadIrisUniforms(stardewcraft$clipLocations);
        stardewcraft$foregroundUniforms.applied();
    }
}
