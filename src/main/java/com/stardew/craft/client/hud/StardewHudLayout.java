package com.stardew.craft.client.hud;

/** Shared SDV pixel-zoom mapping for the time/money HUD and its attached controls. */
final class StardewHudLayout {
    static final int TIME_BG_WIDTH = 72;
    static final int TIME_BG_HEIGHT = 57;

    private static final float SDV_PIXEL_ZOOM = 4.0F;
    private static final int MARGIN_RIGHT = 10;
    private static final int MARGIN_TOP = 10;
    private static final int TOP_SAFE_OFFSET = 24;

    private StardewHudLayout() {}

    static float renderScale(double minecraftGuiScale) {
        return SDV_PIXEL_ZOOM / Math.max(1.0F, (float) minecraftGuiScale);
    }

    static int anchorX(int guiScaledWidth, float renderScale) {
        return Math.round(guiScaledWidth / renderScale) - TIME_BG_WIDTH - MARGIN_RIGHT;
    }

    static int anchorY() {
        return MARGIN_TOP + TOP_SAFE_OFFSET;
    }
}
