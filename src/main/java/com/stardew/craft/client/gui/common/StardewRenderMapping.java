package com.stardew.craft.client.gui.common;

/**
 * Mapping layer between Stardew's pixel-space UI assumptions and Minecraft's GUI-space rendering.
 */
public final class StardewRenderMapping {
    private final int screenWidth;
    private final int screenHeight;
    private final float unitScale;

    public StardewRenderMapping(int screenWidth, int screenHeight, float guiScale) {
        this(screenWidth, screenHeight, (double) (1.0f / Math.max(1.0f, guiScale)));
    }

    private StardewRenderMapping(int screenWidth, int screenHeight, double unitScale) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.unitScale = (float) Math.max(0.0, unitScale);
    }

    /**
     * Creates a mapping which preserves the normal Minecraft GUI scale when the
     * full Stardew canvas fits, and uniformly scales it down when it does not.
     */
    public static StardewRenderMapping fitCanvas(int screenWidth, int screenHeight, float guiScale,
                                                  int canvasWidth, int canvasHeight) {
        float normalScale = 1.0f / Math.max(1.0f, guiScale);
        float widthScale = screenWidth / (float) Math.max(1, canvasWidth);
        float heightScale = screenHeight / (float) Math.max(1, canvasHeight);
        float fittedScale = Math.min(normalScale, Math.min(widthScale, heightScale));
        return new StardewRenderMapping(screenWidth, screenHeight, (double) fittedScale);
    }

    // Maps Stardew pixel unit to MC GUI unit.
    public int ui(int stardewPixels) {
        return Math.round(stardewPixels * unitScale);
    }

    // Equivalent of Stardew's pixelZoom(4) translated to current GUI scale.
    public float s4() {
        return 4.0f * unitScale;
    }

    public int centerX(int width) {
        return screenWidth / 2 - width / 2;
    }

    public int bottomY(int height, int bottomMarginSdvPx) {
        return screenHeight - height - ui(bottomMarginSdvPx);
    }
}
