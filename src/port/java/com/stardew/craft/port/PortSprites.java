package com.stardew.craft.port;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 1.20.2+ {@link TextureAtlasSprite} UV helpers. 1.21 {@code getU/getV(float)} take sprite fractions (0..1) and
 * {@code getUOffset/getVOffset} return fractions; 1.20.1 {@code getU/getV(double)} take 0..16 and the offsets return
 * 0..16. A 1.21 call compiles unchanged on 1.20.1 (float widens to double) but samples the wrong texels, so every
 * call site goes through these helpers, which use the exact 1.21 arithmetic.
 */
@OnlyIn(Dist.CLIENT)
public final class PortSprites {
    private PortSprites() {
    }

    public static float getU(TextureAtlasSprite sprite, float u) {
        float f = sprite.getU1() - sprite.getU0();
        return sprite.getU0() + f * u;
    }

    public static float getV(TextureAtlasSprite sprite, float v) {
        float f = sprite.getV1() - sprite.getV0();
        return sprite.getV0() + f * v;
    }

    public static float getUOffset(TextureAtlasSprite sprite, float offset) {
        float f = sprite.getU1() - sprite.getU0();
        return (offset - sprite.getU0()) / f;
    }

    public static float getVOffset(TextureAtlasSprite sprite, float offset) {
        float f = sprite.getV1() - sprite.getV0();
        return (offset - sprite.getV0()) / f;
    }
}
