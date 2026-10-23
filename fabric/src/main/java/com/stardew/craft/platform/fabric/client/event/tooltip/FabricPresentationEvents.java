package com.stardew.craft.platform.fabric.client.event.tooltip;

import com.stardew.craft.platform.fabric.client.event.RenderFrameEvent;
import com.stardew.craft.platform.fabric.client.event.RenderHandEvent;
import com.stardew.craft.platform.fabric.event.IEventBus;
import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.DeltaTracker;

@Environment(EnvType.CLIENT)
public final class FabricPresentationEvents {
    private static IEventBus installedBus;
    private FabricPresentationEvents() {}
    public static synchronized void install(IEventBus bus) {
        Objects.requireNonNull(bus, "bus");
        if (installedBus == bus) return;
        if (installedBus != null) throw new IllegalStateException("Client presentation events already have an installed bus");
        installedBus = bus;
    }
    public static void hand(RenderHandEvent event, Runnable render) {
        hand(bus(), event, render);
    }
    static void hand(IEventBus bus, RenderHandEvent event, Runnable render) {
        if (!bus.post(event).isCanceled()) render.run();
    }
    public static void framePre(DeltaTracker partialTick) { bus().post(new RenderFrameEvent.Pre(partialTick)); }
    public static void framePost(DeltaTracker partialTick) { bus().post(new RenderFrameEvent.Post(partialTick)); }
    private static IEventBus bus() {
        if (installedBus == null) throw new IllegalStateException("Client presentation event bus is not installed");
        return installedBus;
    }
}
