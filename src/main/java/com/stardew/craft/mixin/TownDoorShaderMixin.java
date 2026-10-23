package com.stardew.craft.mixin;

import com.mojang.blaze3d.shaders.Shader;
import com.mojang.blaze3d.shaders.Uniform;
import com.stardew.craft.client.interior.TownDoorClipping;
import com.stardew.craft.client.interior.TownDoorShaderPatcher;
import net.minecraft.client.renderer.ShaderInstance;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ShaderInstance.class)
public abstract class TownDoorShaderMixin {
    @Shadow @Final private List<Uniform> uniforms;
    @Shadow @Final private String name;
    @Unique @Nullable private Uniform stardewcraft$clipEquation;

    /** Mirrors Immersive Portals: register one real Uniform while ShaderInstance resolves locations. */
    @Inject(method = "updateLocations", at = @At("HEAD"))
    private void stardewcraft$registerClipPlane(CallbackInfo ci) {
        if (!TownDoorShaderPatcher.patchesVanilla(name) || stardewcraft$clipEquation != null) return;
        stardewcraft$clipEquation = new Uniform(TownDoorClipping.UNIFORM, 7, 4, (Shader) (Object) this);
        uniforms.add(stardewcraft$clipEquation);
    }

    @Inject(method = "apply", at = @At("HEAD"))
    private void stardewcraft$uploadClipPlane(CallbackInfo ci) {
        if (stardewcraft$clipEquation != null) {
            TownDoorClipping.updateVanillaUniform(stardewcraft$clipEquation);
        } else {
            TownDoorClipping.setProgramClipping(false);
        }
    }
}
