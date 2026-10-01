package com.stardew.craft.client.gui.common;

/** Reading-size layout rules shared by rendering, controls and headless checks. */
public final class ReadingTextLayout {
    public static final int MIN_PERCENT = 75;
    public static final int MAX_PERCENT = 200;
    public static final int STEP_PERCENT = 25;
    public static final int DEFAULT_PERCENT = 100;

    private ReadingTextLayout() {}

    public static float scale(int percent) {
        return com.stardew.craft.port.PortJava.clamp(percent, MIN_PERCENT, MAX_PERCENT) / 100.0F;
    }

    public static float fitHudScale(float requested, int baseWidth, int baseHeight, int width, int height) {
        return Math.min(requested, Math.min(Math.max(1, width) / (float) baseWidth,
                Math.max(1, height) / (float) baseHeight));
    }
}
