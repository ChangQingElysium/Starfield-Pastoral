package com.stardew.craft.client.gui.overnight;

/**
 * Shared fade math and explicit GUI depth contract for the shipping settlement screen.
 *
 * <p>Vanilla renders item models at z=150 and their count decorations at z=200. A normal
 * {@code GuiGraphics.fill(...)} uses z=0, so it cannot cover shipped item stacks even when the
 * fill call appears later in Java source. These layers keep the transition above every shipping
 * row while preserving the original date-plaque-over-black ordering.</p>
 */
final class ShippingMenuFadeTimeline {
    static final int VANILLA_ITEM_DECORATION_Z = 200;
    static final int CONTENT_BLACKOUT_Z = 10_000;
    static final int OUTRO_FOREGROUND_Z = 11_000;
    static final int FINAL_BLACKOUT_Z = 20_000;

    private ShippingMenuFadeTimeline() {
    }

    static float introBlackAlpha(int remainingMs, int durationMs) {
        if (durationMs <= 0) {
            return 0.0F;
        }
        return clamp(remainingMs / (float) durationMs);
    }

    static float outroBlackAlpha(int remainingMs, int durationMs) {
        if (durationMs <= 0) {
            return 1.0F;
        }
        return 1.0F - clamp(remainingMs / (float) durationMs);
    }

    static int blackArgb(float alpha) {
        int alphaByte = Math.max(0, Math.min(255, Math.round(clamp(alpha) * 255.0F)));
        return alphaByte << 24;
    }

    static boolean hasSafeLayerOrdering() {
        return CONTENT_BLACKOUT_Z > VANILLA_ITEM_DECORATION_Z
            && OUTRO_FOREGROUND_Z > CONTENT_BLACKOUT_Z
            && FINAL_BLACKOUT_Z > OUTRO_FOREGROUND_Z;
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
