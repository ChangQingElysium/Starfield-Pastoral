package com.stardew.craft.platform.fabric.fluid;

import org.joml.Vector3f;

/** Shared RGB policy for fluid meshes and the species-specific underwater fog. */
public final class PondWaterTint {
    public static final int DEFAULT_COLOR = 0x78C8FF;

    private PondWaterTint() {}

    public static int resolve(Integer overrideColor) {
        return overrideColor == null ? DEFAULT_COLOR : overrideColor & 0xFFFFFF;
    }

    public static Vector3f fogColor(int color) {
        return new Vector3f(((color >> 16) & 0xFF) / 255.0F,
                ((color >> 8) & 0xFF) / 255.0F, (color & 0xFF) / 255.0F);
    }
}
