package com.stardew.craft.platform.fabric.client.render;

import com.stardew.craft.platform.fabric.client.event.RenderGuiEvent;
import com.stardew.craft.platform.fabric.event.IEventBus;
import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;

@Environment(EnvType.CLIENT)
public final class FabricGuiRenderEvents {
    private static IEventBus installedBus;
    private FabricGuiRenderEvents() {}

    public static synchronized void install(IEventBus bus) {
        Objects.requireNonNull(bus, "bus");
        if (installedBus == bus) return;
        if (installedBus != null) throw new IllegalStateException("GUI render events already have an installed bus");
        installedBus = bus;
    }

    public static void render(GuiGraphics graphics, DeltaTracker partialTick, Runnable original) {
        if (installedBus == null) throw new IllegalStateException("GUI render event bus is not installed");
        render(installedBus, graphics, partialTick, original);
    }

    static void render(IEventBus bus, GuiGraphics graphics, DeltaTracker partialTick, Runnable original) {
        if (bus.post(new RenderGuiEvent.Pre(graphics, partialTick)).isCanceled()) return;
        original.run();
        bus.post(new RenderGuiEvent.Post(graphics, partialTick));
    }
}
