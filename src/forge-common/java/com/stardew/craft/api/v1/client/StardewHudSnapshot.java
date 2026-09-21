package com.stardew.craft.api.v1.client;

/** Main HUD geometry in Minecraft GUI coordinates, recomputed for the current window and settings.
 * allocatedBounds is the layout editor's reserved group, not a tight visual rectangle.
 * clockBounds is the date/time/weather panel; money and auxiliary controls extend below it.
 * scale converts native Stardew sprite pixels to these GUI units (do not multiply by GUI scale again).
 */
public record StardewHudSnapshot(boolean visible, int screenWidth, int screenHeight, float scale,
                                  Bounds allocatedBounds, Bounds clockBounds) {
    public record Bounds(float x, float y, float width, float height) {
        public float right() { return x + width; }
        public float bottom() { return y + height; }
    }
}
