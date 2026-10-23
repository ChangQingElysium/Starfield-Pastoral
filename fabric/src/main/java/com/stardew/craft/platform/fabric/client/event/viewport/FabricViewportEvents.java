package com.stardew.craft.platform.fabric.client.event.viewport;

import com.mojang.blaze3d.shaders.FogShape;
import com.stardew.craft.platform.fabric.client.event.ViewportEvent;
import com.stardew.craft.platform.fabric.event.IEventBus;
import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.level.material.FogType;

@Environment(EnvType.CLIENT)
public final class FabricViewportEvents {
    private static IEventBus installedBus;
    private FabricViewportEvents() {}
    public static synchronized void install(IEventBus bus) {
        Objects.requireNonNull(bus, "bus");
        if (installedBus == bus) return;
        if (installedBus != null) throw new IllegalStateException("Viewport events already have an installed bus");
        installedBus = bus;
    }
    public static ViewportEvent.ComputeFogColor color(Camera camera, float partialTick, float red, float green, float blue) {
        return bus().post(new ViewportEvent.ComputeFogColor(camera, partialTick, red, green, blue));
    }
    @FunctionalInterface public interface FogSink { void set(float near, float far, FogShape shape); }
    public static void fog(FogRenderer.FogMode mode, FogType type, Camera camera, float partialTick,
                           float near, float far, FogShape shape, FogSink sink) {
        fog(bus(), new ViewportEvent.RenderFog(mode, type, camera, partialTick, near, far, shape), sink);
    }
    static void fog(IEventBus bus, ViewportEvent.RenderFog event, FogSink sink) {
        if (bus.post(event).isCanceled()) sink.set(event.getNearPlaneDistance(), event.getFarPlaneDistance(), event.getFogShape());
    }
    private static IEventBus bus() {
        if (installedBus == null) throw new IllegalStateException("Viewport event bus is not installed");
        return installedBus;
    }
}
