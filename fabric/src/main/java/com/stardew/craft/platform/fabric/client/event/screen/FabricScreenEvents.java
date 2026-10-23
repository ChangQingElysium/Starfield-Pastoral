package com.stardew.craft.platform.fabric.client.event.screen;

import com.stardew.craft.platform.fabric.client.event.ScreenEvent;
import com.stardew.craft.platform.fabric.event.IEventBus;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

@Environment(EnvType.CLIENT)
public final class FabricScreenEvents {
    private static IEventBus installedBus;
    private FabricScreenEvents() {}
    public static synchronized void install(IEventBus bus) {
        Objects.requireNonNull(bus, "bus");
        if (installedBus == bus) return;
        if (installedBus != null) throw new IllegalStateException("Screen event bus already installed");
        installedBus = bus;
    }
    public static IEventBus bus() {
        if (installedBus == null) throw new IllegalStateException("Screen event bus is not installed");
        return installedBus;
    }
    public static void render(IEventBus bus, Screen screen, GuiGraphics graphics, int x, int y, float partialTick, Runnable original) {
        if (!bus.post(new ScreenEvent.Render.Pre(screen, graphics, x, y, partialTick)).isCanceled()) original.run();
        // Neo fires Screen.Render.Post even if Pre cancels. This differs from the HUD/entity contracts.
        bus.post(new ScreenEvent.Render.Post(screen, graphics, x, y, partialTick));
    }
    public static Transition transition(IEventBus bus, Screen current, Screen requested) {
        Screen target = requested;
        if (target != null) {
            var event = bus.post(new ScreenEvent.Opening(current, target));
            if (event.isCanceled()) return new Transition(true, current);
            target = event.getNewScreen();
        }
        if (current != null && current != target) { bus.post(new ScreenEvent.Closing(current)); current.removed(); }
        return new Transition(false, target);
    }
    public record Transition(boolean canceled, Screen target) {}
    public static boolean click(IEventBus bus, Screen screen, double x, double y, int button, BooleanSupplier original) {
        if (bus.post(new ScreenEvent.MouseButtonPressed.Pre(screen, x, y, button)).isCanceled()) return true;
        return bus.post(new ScreenEvent.MouseButtonPressed.Post(screen, x, y, button, original.getAsBoolean())).getClickResult();
    }
    public static boolean release(IEventBus bus, Screen screen, double x, double y, int button, BooleanSupplier original) {
        if (bus.post(new ScreenEvent.MouseButtonReleased.Pre(screen, x, y, button)).isCanceled()) return true;
        return bus.post(new ScreenEvent.MouseButtonReleased.Post(screen, x, y, button, original.getAsBoolean())).getReleaseResult();
    }
    public static boolean drag(IEventBus bus, Screen screen, double x, double y, int button, double dx, double dy, BooleanSupplier original) {
        if (bus.post(new ScreenEvent.MouseDragged.Pre(screen, x, y, button, dx, dy)).isCanceled()) return true;
        boolean handled = original.getAsBoolean();
        if (!handled) bus.post(new ScreenEvent.MouseDragged.Post(screen, x, y, button, dx, dy));
        return handled;
    }
    public static boolean scroll(IEventBus bus, Screen screen, double x, double y, double dx, double dy, BooleanSupplier original) {
        if (bus.post(new ScreenEvent.MouseScrolled.Pre(screen, x, y, dx, dy)).isCanceled()) return true;
        boolean handled = original.getAsBoolean();
        if (!handled) bus.post(new ScreenEvent.MouseScrolled.Post(screen, x, y, dx, dy));
        return handled;
    }
    public static boolean key(IEventBus bus, Screen screen, int key, int scan, int modifiers, boolean pressed, BooleanSupplier original) {
        var pre = pressed ? new ScreenEvent.KeyPressed.Pre(screen, key, scan, modifiers) : new ScreenEvent.KeyReleased.Pre(screen, key, scan, modifiers);
        if (bus.post(pre).isCanceled()) return true;
        if (original.getAsBoolean()) return true;
        var post = pressed ? new ScreenEvent.KeyPressed.Post(screen, key, scan, modifiers) : new ScreenEvent.KeyReleased.Post(screen, key, scan, modifiers);
        return bus.post(post).isCanceled();
    }
}
