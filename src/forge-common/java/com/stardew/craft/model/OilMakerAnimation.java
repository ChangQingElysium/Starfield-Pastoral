package com.stardew.craft.model;

/** Oil maker source timing, expressed independently of either renderer. */
public final class OilMakerAnimation {
    private OilMakerAnimation() {}

    public static int particleFrame(float millis) {
        return (int) (Math.max(0, millis) / 80) % 6;
    }

    public static float particleAlpha(float millis) {
        return Math.max(0, 1 - Math.max(0, millis) * 0.0003F);
    }

    // Object.getScale: 0.1 per source frame at 60 Hz, wrapping at 10.
    private static float phase(float ticks) {
        return ((ticks * 0.3F) % 10 + 10) % 10;
    }

    public static float widthScale(float ticks) {
        float phase = phase(ticks);
        return 1 + Math.min(phase, 10 - phase) / 16;
    }

    public static float heightScale(float ticks) {
        return 1 + Math.abs(phase(ticks) - 5) / 64;
    }
}
