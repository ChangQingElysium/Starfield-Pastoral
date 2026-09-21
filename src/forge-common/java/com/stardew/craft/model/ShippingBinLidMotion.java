package com.stardew.craft.model;

import net.minecraft.util.Mth;

/** Client-only transient pose state; no dependency on client classes. */
public final class ShippingBinLidMotion {
    private static final float[] ANGLES = {0, 5, 11, 19, 29, 41, 53, 64, 74, 81, 86, 89, 90};
    private float previous;
    private float progress;
    private boolean initialized;

    public void tick(boolean open, float durationTicks) {
        if (!initialized) {
            progress = open ? 1 : 0;
            initialized = true;
        }
        previous = progress;
        progress = Mth.clamp(progress + (open ? 1 : -1) / durationTicks, 0, 1);
    }
    public float radians(float partialTick) {
        float frame = Mth.lerp(partialTick, previous, progress) * 12;
        int lower = Math.min(11, (int) frame);
        return Mth.lerp(frame - lower, ANGLES[lower], ANGLES[lower + 1]) * Mth.DEG_TO_RAD;
    }
}
