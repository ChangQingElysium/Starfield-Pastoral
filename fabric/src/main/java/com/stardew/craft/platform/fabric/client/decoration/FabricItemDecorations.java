package com.stardew.craft.platform.fabric.client.decoration;

import com.stardew.craft.platform.fabric.client.event.RegisterItemDecorationsEvent;
import com.stardew.craft.platform.fabric.event.IEventBus;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

@Environment(EnvType.CLIENT)
public final class FabricItemDecorations {
    private static IEventBus installedBus;
    private static boolean initialized;
    private static Map<Item, List<IItemDecorator>> decorators = Map.of();
    private FabricItemDecorations() {}
    public static synchronized void install(IEventBus bus) {
        Objects.requireNonNull(bus); if (installedBus == bus) return;
        if (installedBus != null) throw new IllegalStateException("Item decoration bus already installed"); installedBus = bus;
    }
    public static synchronized void initialize() {
        if (installedBus == null) throw new IllegalStateException("Item decoration bus is not installed");
        if (initialized) throw new IllegalStateException("Item decorations already initialized"); initialized = true;
        decorators = register(installedBus);
    }
    static Map<Item, List<IItemDecorator>> register(IEventBus bus) {
        Map<Item, List<IItemDecorator>> registered = new IdentityHashMap<>(); bus.post(new RegisterItemDecorationsEvent(registered));
        Map<Item, List<IItemDecorator>> frozen = new IdentityHashMap<>(); registered.forEach((item, callbacks) -> frozen.put(item, List.copyOf(callbacks)));
        return java.util.Collections.unmodifiableMap(frozen);
    }
    public static void render(GuiGraphics graphics, Font font, ItemStack stack, int x, int y) {
        if (stack.isEmpty()) return;
        render(decorators.getOrDefault(stack.getItem(), List.of()), graphics, font, stack, x, y, DecorationGlState.ACCESS);
    }
    static void render(List<IItemDecorator> callbacks, GuiGraphics graphics, Font font, ItemStack stack, int x, int y, StateAccess states) {
        if (callbacks.isEmpty()) return;
        Runnable restore = states.backup();
        try {
            states.reset();
            for (var callback : callbacks) if (callback.render(graphics, font, stack, x, y)) states.reset();
        } finally { restore.run(); }
    }
    interface StateAccess { Runnable backup(); void reset(); }
}
