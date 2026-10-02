package com.stardew.craft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.stardew.craft.client.light.EmbeddiumLightBridge;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

/** PORT(1.20.1): Embeddium writes baked quads, not Sodium 0.6's FRAPI MutableQuadView. */
@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer", remap = false)
public abstract class SodiumColoredLightMixin {
    @WrapOperation(method = "writeGeometry", at = @At(value = "INVOKE",
            target = "Lorg/embeddedt/embeddium/render/chunk/ChunkColorWriter;writeColor(IF)I"), require = 1)
    private int stardewcraft$rgb(@Coerce Object writer, int color, float brightness, Operation<Integer> original,
            @Coerce Object context, @Coerce Object builder, Vec3 offset, @Coerce Object material,
            @Coerce Object quad, int[] colors, @Coerce Object lightData, @Local(index = 12) int vertex) {
        // The audited source index is after quad reorientation. Keep Embeddium's AO encoder intact.
        return original.call(writer, EmbeddiumLightBridge.color(context, quad, lightData, vertex, color), brightness);
    }

    @WrapOperation(method = "writeGeometry", at = @At(value = "INVOKE",
            target = "Lme/jellysquid/mods/sodium/client/util/ModelQuadUtil;mergeBakedLight(II)I"), require = 1)
    private int stardewcraft$movingLight(int bakedLight, int calculatedLight, Operation<Integer> original,
            @Coerce Object context, @Coerce Object builder, Vec3 offset, @Coerce Object material,
            @Coerce Object quad, int[] colors, @Coerce Object lightData, @Local(index = 12) int vertex) {
        return EmbeddiumLightBridge.light(context, quad, vertex, original.call(bakedLight, calculatedLight));
    }
}
