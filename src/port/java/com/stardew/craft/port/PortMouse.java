package com.stardew.craft.port;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * PORT(1.20.1): 1.20.2+ mouse input values that 1.20.1 does not hand to listeners, maintained by
 * {@code PortMouseHandlerScreenInputMixin}.
 *
 * <p>1.21.1 {@code MouseHandler#onScroll} passes both axes ({@code scrollX}, {@code scrollY}, each
 * {@code (discreteMouseScroll ? signum(offset) : offset) * mouseWheelSensitivity}) to screens and to NeoForge's scroll
 * events. 1.20.1 GLFW still reports the horizontal offset but vanilla folds it into one value (on macOS the horizontal
 * offset replaces a zero vertical one). While {@code onScroll} runs, {@link #scrollDeltaX()} /
 * {@link #scrollDeltaY()} are the 1.21.1 values of the current event (0 outside a scroll event).
 */
@OnlyIn(Dist.CLIENT)
public final class PortMouse {
    private static double scrollDeltaX;
    private static double scrollDeltaY;

    private PortMouse() {}

    /** 1.21.1 {@code getScrollDeltaX()} of the scroll event being dispatched. */
    public static double scrollDeltaX() {
        return scrollDeltaX;
    }

    /** 1.21.1 {@code getScrollDeltaY()} of the scroll event being dispatched. */
    public static double scrollDeltaY() {
        return scrollDeltaY;
    }

    public static void setScroll(double x, double y) {
        scrollDeltaX = x;
        scrollDeltaY = y;
    }
}
