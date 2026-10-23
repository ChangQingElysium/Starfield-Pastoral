package com.stardew.craft.mixin;

import com.stardew.craft.client.interior.TownDoorShaderPatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.LinkedHashMap;

/** Iris terrain transformation hook adapted from Immersive Portals (Apache-2.0). */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.pipeline.transform.TransformPatcher", remap = false)
public abstract class TownDoorIrisTransformMixin {
    @Inject(method = "transformInternal", at = @At("RETURN"), cancellable = true, require = 1)
    private static void stardewcraft$patch(String name, Map<?, String> inputs, @Coerce Object parameters,
                                           CallbackInfoReturnable<Map<?, String>> cir) {
        Map<?, String> output = cir.getReturnValue();
        if (output == null) return;
        try {
            String patch = String.valueOf(parameters.getClass().getField("patch").get(parameters));
            if (!"SODIUM".equals(patch) && !"VANILLA".equals(patch)) return;
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot identify Iris geometry transformation", error);
        }
        for (Object key : output.keySet()) {
            if (!"FRAGMENT".equals(String.valueOf(key))) continue;
            String source = output.get(key);
            if (source == null) return;
            Map<Object, String> writable = new LinkedHashMap<>(output);
            writable.put(key, TownDoorShaderPatcher.transformIrisFragment(source));
            cir.setReturnValue(writable);
            return;
        }
    }
}
