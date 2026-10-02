package com.stardew.craft.mixin;

import com.stardew.craft.client.light.RingLightRenderer;
import com.stardew.craft.client.render.EmbeddiumQuadAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Apply after Sodium's integer light cache, preserving fractional per-vertex brightness. */
@Pseudo
@Mixin(targets = {
        "me.jellysquid.mods.sodium.client.model.light.smooth.SmoothLightPipeline",
        "me.jellysquid.mods.sodium.client.model.light.flat.FlatLightPipeline"
}, remap = false)
public abstract class SodiumRingLightPipelineMixin {
    // PORT(1.20.1): Embeddium has no Sodium 0.6 'enhanced' argument.
    @Inject(method = "calculate", at = @At("RETURN"), require = 1)
    private void stardewcraft$ringLight(@Coerce Object quad, BlockPos pos, @Coerce Object lightData,
                                      Direction cullFace, Direction lightFace, boolean shade,
                                      CallbackInfo ci) {
        EmbeddiumQuadAccess.Coordinates vertices = (EmbeddiumQuadAccess.Coordinates) quad;
        int[] lightmap = ((SodiumRingLightDataAccessor) lightData).stardewcraft$getLightmap();
        for (int i = 0; i < 4; i++) {
            lightmap[i] = RingLightRenderer.lightColor(pos.getX() + vertices.stardewcraft$getX(i),
                    pos.getY() + vertices.stardewcraft$getY(i), pos.getZ() + vertices.stardewcraft$getZ(i), lightmap[i]);
        }
    }
}
