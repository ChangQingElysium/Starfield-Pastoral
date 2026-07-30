package com.stardew.craft.client.gui.casino;

/**
 * Converts Stardew's physical-pixel minigame coordinates into Minecraft GUI
 * coordinates. The normal mapping is the same one used by the fair slingshot
 * game: one Stardew pixel is one physical window pixel. On unusually small
 * windows the complete 1280x720 casino canvas is fitted uniformly instead of
 * clipping controls.
 */
final class CasinoViewport {
    static final int MIN_SOURCE_WIDTH = 1280;
    static final int MIN_SOURCE_HEIGHT = 720;

    private final int screenWidth;
    private final int screenHeight;
    private final float scale;
    private final int sourceWidth;
    private final int sourceHeight;

    CasinoViewport(int screenWidth, int screenHeight, float guiScale) {
        this.screenWidth = Math.max(1, screenWidth);
        this.screenHeight = Math.max(1, screenHeight);
        float normalScale = 1.0F / Math.max(1.0F, guiScale);
        float fitWidth = this.screenWidth / (float) MIN_SOURCE_WIDTH;
        float fitHeight = this.screenHeight / (float) MIN_SOURCE_HEIGHT;
        this.scale = Math.max(0.01F, Math.min(normalScale, Math.min(fitWidth, fitHeight)));
        this.sourceWidth = Math.max(MIN_SOURCE_WIDTH, Math.round(this.screenWidth / this.scale));
        this.sourceHeight = Math.max(MIN_SOURCE_HEIGHT, Math.round(this.screenHeight / this.scale));
    }

    int ui(int sourcePixels) {
        return Math.round(sourcePixels * scale);
    }

    int sourceWidth() {
        return sourceWidth;
    }

    int sourceHeight() {
        return sourceHeight;
    }

    int centerX(int sourceOffset) {
        return screenWidth / 2 + ui(sourceOffset);
    }

    int centerY(int sourceOffset) {
        return screenHeight / 2 + ui(sourceOffset);
    }

    float pixelZoom() {
        return 4.0F * scale;
    }

    float effectiveGuiScale() {
        return 1.0F / scale;
    }

    Rect rect(int sourceX, int sourceY, int sourceWidth, int sourceHeight) {
        return new Rect(ui(sourceX), ui(sourceY), ui(sourceWidth), ui(sourceHeight));
    }

    boolean containsSourceRect(int sourceX, int sourceY, int sourceWidth, int sourceHeight) {
        return sourceX >= 0
                && sourceY >= 0
                && sourceX + sourceWidth <= this.sourceWidth
                && sourceY + sourceHeight <= this.sourceHeight;
    }

    record Rect(int x, int y, int width, int height) {
        static final Rect ZERO = new Rect(0, 0, 0, 0);

        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width
                    && mouseY >= y && mouseY < y + height;
        }
    }
}
