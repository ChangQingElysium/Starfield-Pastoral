package com.stardew.craft.mixin;

import com.stardew.craft.client.interior.TownDoorShaderPatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/** Iris terrain transformation hook adapted from Immersive Portals (Apache-2.0). */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.pipeline.transform.TransformPatcher", remap = false)
public abstract class TownDoorIrisTransformMixin {
    @Inject(method = "transformInternal", at = @At("RETURN"), cancellable = true, require = 1)
    private static void stardewcraft$patch(String name, Map<?, String> inputs, @Coerce Object parameters,
                                           CallbackInfoReturnable<Map<?, String>> cir) {
        Map<?, String> output = cir.getReturnValue();
        if (output == null) return;
        for (Object key : output.keySet()) {
            if (!"VERTEX".equals(String.valueOf(key))) continue;
            String source = output.get(key);
            if (source == null) return;
            @SuppressWarnings({"rawtypes", "unchecked"}) Map writable = output;
            writable.put(key, TownDoorShaderPatcher.transformIrisVertex(source));
            return;
        }
    }
}
