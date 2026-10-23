package com.stardew.craft.mixin;

import com.stardew.craft.client.interior.TownDoorShaderPatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Packs can omit programs; Iris synthesizes those fallbacks without calling TransformPatcher. */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.pipeline.fallback.ShaderSynthesizer", remap = false)
public abstract class TownDoorIrisShaderSynthesizerMixin {
    @Inject(method = "fsh", at = @At("RETURN"), cancellable = true, require = 1)
    private static void stardewcraft$clipFallback(CallbackInfoReturnable<String> cir) {
        cir.setReturnValue(TownDoorShaderPatcher.transformIrisFragment(cir.getReturnValue()));
    }
}
