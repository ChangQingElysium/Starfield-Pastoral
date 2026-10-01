package com.stardew.craft.client.gui.common;

import com.mojang.blaze3d.platform.Window;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.font.StardewFonts;
import com.stardew.craft.client.gui.ReadingTextSettingsScreen;
import com.stardew.craft.client.hud.StardewHudLayoutEditorScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.client.event.ScreenEvent;

import java.util.Map;
import java.util.WeakHashMap;

/** Whole-page reading zoom keeps authored text, artwork and hit boxes in proportion. */
@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public final class StardewReadingZoom {
    private static final Map<Screen, State> STATES = new WeakHashMap<>();
    private static final class State {
        double x = .5, y = .5, grab;
        int dragging;
        ReadingZoomMath.Zoom zoom;
    }
    private StardewReadingZoom() {}

    private static boolean enabled(Screen screen) {
        // Keep the preference controls visible while changing zoom; the HUD editor
        // already previews each element with its final configured scale.
        return screen != null && screen.getClass().getName().startsWith("com.stardew.craft.")
                && !(screen instanceof ReadingTextSettingsScreen)
                && !(screen instanceof StardewHudLayoutEditorScreen)
                && StardewFonts.readingScale() != 1.0F;
    }

    public static boolean supportsAdditional(Screen screen) { return enabled(screen); }

    public static GuiLayoutMath.Viewport apply(Screen screen, Window window, GuiLayoutMath.Viewport base) {
        if (!enabled(screen)) return base;
        var state = STATES.computeIfAbsent(screen, ignored -> new State());
        state.zoom = ReadingZoomMath.calculate(base, window.getWidth(), window.getHeight(),
                StardewFonts.readingScale(), state.x, state.y);
        return state.zoom.viewport();
    }

    public static ReadingZoomMath.Zoom current(Screen screen) {
        var state = enabled(screen) ? STATES.get(screen) : null;
        return state == null ? null : state.zoom;
    }

    @SubscribeEvent public static void tooltip(net.minecraftforge.client.event.RenderTooltipEvent.GatherComponents event) {
        var zoom = current(Minecraft.getInstance().screen);
        if (zoom == null || StardewGuiViewport.active() == null) return;
        var v = zoom.viewport();
        int width = Math.max(1, (int) (zoom.viewWidth() / (v.scale() * v.windowScale())) - 20);
        event.setMaxWidth(event.getMaxWidth() > 0 ? Math.min(event.getMaxWidth(), width) : width);
    }

    public static void drawScrollbars(Screen screen, GuiGraphics g) {
        var zoom = current(screen);
        if (zoom == null) return;
        // Called after restoring the screen pose. Draw rails in actual framebuffer units.
        var previous = StardewGuiViewport.enter(null);
        try {
            double windowScale = Minecraft.getInstance().getWindow().getGuiScale();
            g.pose().pushPose();
            g.pose().scale((float) (1 / windowScale), (float) (1 / windowScale), 1);
            drawBar(g, zoom.horizontal());
            drawBar(g, zoom.vertical());
            if (zoom.horizontal() != null && zoom.vertical() != null) {
                g.fill(zoom.viewWidth(), zoom.viewHeight(), zoom.viewWidth() + ReadingZoomMath.RAIL_PIXELS,
                        zoom.viewHeight() + ReadingZoomMath.RAIL_PIXELS, 0xFF3E3026);
            }
            g.pose().popPose();
        } finally { StardewGuiViewport.restore(previous); }
    }

    private static void drawBar(GuiGraphics g, ReadingZoomMath.Scrollbar bar) {
        if (bar == null) return;
        int x = (int) bar.x(), y = (int) bar.y();
        int w = (int) bar.width(), h = (int) bar.height();
        g.fill(x, y, x + w, y + h, 0xFF3E3026);
        int start = (int) Math.round(bar.thumbStart()), length = (int) Math.round(bar.thumbLength());
        if (bar.horizontal()) g.fill(x + start, y + 3, x + start + length, y + h - 3, 0xFFE1BD82);
        else g.fill(x + 3, y + start, x + w - 3, y + start + length, 0xFFE1BD82);
    }

    private static double mouseX() {
        var mc = Minecraft.getInstance();
        return mc.mouseHandler.xpos() * mc.getWindow().getWidth() / mc.getWindow().getScreenWidth();
    }
    private static double mouseY() {
        var mc = Minecraft.getInstance();
        return mc.mouseHandler.ypos() * mc.getWindow().getHeight() / mc.getWindow().getScreenHeight();
    }
    private static ReadingZoomMath.Scrollbar barAt(ReadingZoomMath.Zoom zoom, double x, double y) {
        if (zoom.horizontal() != null && zoom.horizontal().contains(x, y)) return zoom.horizontal();
        if (zoom.vertical() != null && zoom.vertical().contains(x, y)) return zoom.vertical();
        return null;
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST) public static void press(ScreenEvent.MouseButtonPressed.Pre event) {
        var zoom = current(event.getScreen());
        if (zoom == null) return;
        var bar = barAt(zoom, mouseX(), mouseY());
        if (bar == null) {
            if (mouseX() >= zoom.viewWidth() || mouseY() >= zoom.viewHeight()) event.setCanceled(true);
            return;
        }
        event.setCanceled(true);
        if (event.getButton() != 0) return;
        var state = STATES.get(event.getScreen());
        double pointer = bar.horizontal() ? mouseX() : mouseY();
        double relative = pointer - (bar.horizontal() ? bar.x() : bar.y());
        state.grab = relative >= bar.thumbStart() && relative < bar.thumbStart() + bar.thumbLength()
                ? relative - bar.thumbStart() : bar.thumbLength() / 2;
        state.dragging = bar.horizontal() ? 1 : 2;
        moveThumb(state, bar, pointer);
        event.setCanceled(true);
    }

    private static void moveThumb(State state, ReadingZoomMath.Scrollbar bar, double pointer) {
        if (bar.horizontal()) state.x = bar.fractionAt(pointer, state.grab);
        else state.y = bar.fractionAt(pointer, state.grab);
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST) public static void drag(ScreenEvent.MouseDragged.Pre event) {
        var zoom = current(event.getScreen());
        if (zoom == null) return;
        var state = STATES.get(event.getScreen());
        if (state.dragging == 0) return;
        var bar = state.dragging == 1 ? zoom.horizontal() : zoom.vertical();
        if (bar != null) moveThumb(state, bar, state.dragging == 1 ? mouseX() : mouseY());
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST) public static void release(ScreenEvent.MouseButtonReleased.Pre event) {
        var state = STATES.get(event.getScreen());
        if (state != null && state.dragging != 0 && event.getButton() == 0) {
            state.dragging = 0;
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST) public static void scroll(ScreenEvent.MouseScrolled.Pre event) {
        var zoom = current(event.getScreen());
        if (zoom == null) return;
        var bar = barAt(zoom, mouseX(), mouseY());
        if (bar == null && !Screen.hasAltDown() && !Screen.hasShiftDown()) return;
        boolean horizontal = bar != null ? bar.horizontal() : Screen.hasShiftDown();
        // PORT(1.20.1): 1.20.1 has no horizontal scroll; getScrollDelta() is the vertical delta.
        double delta = event.getScrollDelta();
        if (pan(event.getScreen(), horizontal ? -delta * 64 : 0, horizontal ? 0 : -delta * 64)) event.setCanceled(true);
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST) public static void key(ScreenEvent.KeyPressed.Pre event) {
        if ((event.getModifiers() & 4) == 0 || event.getScreen().getFocused() instanceof EditBox) return;
        int key = event.getKeyCode();
        double dx = key == 263 ? -64 : key == 262 ? 64 : 0;
        double dy = key == 265 ? -64 : key == 264 ? 64 : 0;
        if ((dx != 0 || dy != 0) && pan(event.getScreen(), dx, dy)) event.setCanceled(true);
    }

    private static boolean pan(Screen screen, double dx, double dy) {
        var zoom = current(screen);
        if (zoom == null) return false;
        var state = STATES.get(screen);
        if (zoom.overflowX() > 0) state.x = com.stardew.craft.port.PortJava.clamp(state.x + dx / zoom.overflowX(), 0, 1);
        if (zoom.overflowY() > 0) state.y = com.stardew.craft.port.PortJava.clamp(state.y + dy / zoom.overflowY(), 0, 1);
        return dx != 0 && zoom.overflowX() > 0 || dy != 0 && zoom.overflowY() > 0;
    }

    /** Native Tab navigation must not leave the newly focused control off screen. */
    public static void revealFocus(Screen screen) {
        var zoom = current(screen);
        if (zoom == null || !(screen.getFocused() instanceof AbstractWidget widget)) return;
        var v = zoom.viewport();
        double scale = v.scale() * v.windowScale();
        double left = (v.x() + widget.getX() * v.scale()) * v.windowScale();
        double top = (v.y() + widget.getY() * v.scale()) * v.windowScale();
        double right = left + widget.getWidth() * scale, bottom = top + widget.getHeight() * scale;
        double dx = left < 0 ? left : right > zoom.viewWidth() ? right - zoom.viewWidth() : 0;
        double dy = top < 0 ? top : bottom > zoom.viewHeight() ? bottom - zoom.viewHeight() : 0;
        pan(screen, dx, dy);
    }
}
