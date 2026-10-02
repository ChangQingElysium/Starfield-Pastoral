package com.stardew.craft.port;

import com.stardew.craft.client.gui.common.GuiLayoutMath;

/** Pure 1.21.1 screen movement accumulation/mapping, shared with the headless input regression check. */
public final class PortMouseMovement {
    private double dx;
    private double dy;

    public void accumulate(double previousX, double previousY, double x, double y, boolean ignoreFirstMove,
            boolean activeWindow) {
        if (!ignoreFirstMove && activeWindow) {
            dx += x - previousX;
            dy += y - previousY;
        }
    }

    public boolean hasDelta() {
        return dx != 0.0 || dy != 0.0;
    }

    public Frame frame(GuiLayoutMath.Viewport layout, double x, double y, int screenWidth, int screenHeight,
            int guiWidth, int guiHeight) {
        double rawX = layout == null ? x : layout.windowMouseX(x, screenWidth);
        double rawY = layout == null ? y : layout.windowMouseY(y, screenHeight);
        double accX = layout == null ? dx : layout.windowDeltaX(dx);
        double accY = layout == null ? dy : layout.windowDeltaY(dy);
        return new Frame(rawX * guiWidth / screenWidth, rawY * guiHeight / screenHeight,
                accX * guiWidth / screenWidth, accY * guiHeight / screenHeight);
    }

    public void reset() {
        dx = 0.0;
        dy = 0.0;
    }

    public record Frame(double mouseX, double mouseY, double dragX, double dragY) {}
}
