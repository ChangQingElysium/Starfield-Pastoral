package com.stardew.craft.platform.fabric.client.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Gui;

/** Per-Gui bar heights, including rows reserved by canceled health/food layers. */
@Environment(EnvType.CLIENT)
public final class GuiHudLayout {
    private GuiHudLayout() {}

    public interface Access { State stardewcraft$hudLayout(); }

    public static final class State {
        private int left = 39;
        private int right = 39;
        public void reset() { left = 39; right = 39; }
        public int left() { return left; }
        public int right() { return right; }
        public void addLeft(int height) { left += height; }
        public void addRight(int height) { right += height; }
        public int selectedItemHeight() { return Math.max(59, Math.max(left, right)); }
        public int addVehicleRows(int hearts) {
            int rows = (hearts + 9) / 10;
            right += rows * 10;
            return rows;
        }
    }

    public static void reset(Gui gui) { state(gui).reset(); }
    public static void addLeft(Gui gui, int height) { state(gui).addLeft(height); }
    public static void addRight(Gui gui, int height) { state(gui).addRight(height); }

    private static State state(Gui gui) {
        if (!(gui instanceof Access access)) throw new IllegalStateException("GUI height mixin is not installed");
        return access.stardewcraft$hudLayout();
    }
}
