package com.stardew.craft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stardew.craft.client.interior.TownDoorShaderPatcher;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Sodium shader hook adapted from Immersive Portals (Apache-2.0), with no Sodium linkage. */
@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.gl.shader.ShaderLoader", remap = false)
public abstract class TownDoorSodiumShaderLoaderMixin {
    @WrapOperation(method = "loadShader", at = @At(value = "INVOKE",
            target = "Lme/jellysquid/mods/sodium/client/gl/shader/ShaderLoader;getShaderSource(Lnet/minecraft/resources/ResourceLocation;)Ljava/lang/String;",
            remap = false), require = 1)
    private static String stardewcraft$patch(ResourceLocation name, Operation<String> original) {
        // The transform itself recognizes Sodium's vertex source. Avoid a fragile local capture of
        // ShaderType: a missed capture previously disabled clipping without any startup failure.
        return TownDoorShaderPatcher.transformSodiumVertex(original.call(name));
    }
}
