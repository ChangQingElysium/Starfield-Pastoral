package com.stardew.craft.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.stardew.craft.client.interior.TownDoorRenderContext;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Biome, rainfall and temperature belong to the viewed location, including water tint decisions. */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.uniforms.BiomeUniforms", remap = false)
public abstract class TownDoorIrisBiomeUniformsMixin {
    @ModifyExpressionValue(method = "*", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;blockPosition()Lnet/minecraft/core/BlockPos;", remap = true), require = 1)
    private static BlockPos stardewcraft$destinationBiome(BlockPos original) {
        return TownDoorRenderContext.isRendering()
                ? BlockPos.containing(TownDoorRenderContext.cameraPosition()) : original;
    }
}
