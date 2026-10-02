package com.stardew.craft.client.light;

import com.stardew.craft.mixin.SodiumColoredLightPositionAccessor;
import com.stardew.craft.client.render.EmbeddiumQuadAccess;
import com.stardew.craft.mixin.SodiumRingLightDataAccessor;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;

/** Optional renderer types stay behind Mixin interfaces; no Embeddium linkage when absent. */
public final class EmbeddiumLightBridge {
    private EmbeddiumLightBridge() {}

    public static int color(Object context, Object quad, Object lightData, int vertex, int color) {
        if (!ColoredLightEngine.active() || !((BakedQuad) quad).isShade()) return color;
        var q = (EmbeddiumQuadAccess.Lighting) quad;
        BlockPos pos = ((SodiumColoredLightPositionAccessor) context).stardewcraft$getLightPosition();
        var face = q.stardewcraft$getLightFace();
        int light = mergeLight(q.stardewcraft$getLight(vertex),
                ((SodiumRingLightDataAccessor) lightData).stardewcraft$getLightmap()[vertex]);
        int tint = ColoredLightEngine.tint(pos.getX() + q.stardewcraft$getX(vertex) + face.getStepX() * .02,
                pos.getY() + q.stardewcraft$getY(vertex) + face.getStepY() * .02,
                pos.getZ() + q.stardewcraft$getZ(vertex) + face.getStepZ() * .02, light);
        return multiplyAbgr(color, tint);
    }

    public static int light(Object context, Object quad, int vertex, int light) {
        if (!ColoredLightEngine.active()) return light;
        var q = (EmbeddiumQuadAccess.Lighting) quad;
        BlockPos pos = ((SodiumColoredLightPositionAccessor) context).stardewcraft$getLightPosition();
        var face = q.stardewcraft$getLightFace();
        return ColoredLightEngine.light(pos.getX() + q.stardewcraft$getX(vertex) + face.getStepX() * .02,
                pos.getY() + q.stardewcraft$getY(vertex) + face.getStepY() * .02,
                pos.getZ() + q.stardewcraft$getZ(vertex) + face.getStepZ() * .02, light);
    }

    // BakedQuad/ChunkVertexEncoder color words are ABGR on the native little-endian vertex buffer;
    // Sodium 0.6's FRAPI color() used ARGB. Swap only tint's red/blue, never alpha or AO brightness.
    public static int multiplyAbgr(int color, int rgbTint) {
        int abgrTint = (rgbTint & 255) << 16 | rgbTint & 0xff00 | rgbTint >>> 16 & 255;
        return ColoredLightEngine.multiplyArgb(color, abgrTint);
    }

    /** Exact Embeddium ModelQuadUtil.mergeBakedLight semantics for tint's sky-light input. */
    public static int mergeLight(int baked, int calculated) {
        if (baked == 0) return calculated;
        return Math.max(baked >>> 16 & 255, calculated >>> 16 & 255) << 16
                | Math.max(baked & 255, calculated & 255);
    }
}
