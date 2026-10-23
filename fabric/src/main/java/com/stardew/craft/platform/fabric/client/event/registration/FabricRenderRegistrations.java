package com.stardew.craft.platform.fabric.client.event.registration;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.stardew.craft.platform.fabric.client.event.RegisterDimensionSpecialEffectsEvent;
import com.stardew.craft.platform.fabric.client.event.RegisterRenderBuffersEvent;
import com.stardew.craft.platform.mixin.client.event.registration.mixin.DimensionEffectsAccess;
import com.stardew.craft.platform.fabric.event.IEventBus;
import java.util.Objects;
import java.util.SequencedMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.RenderType;

@Environment(EnvType.CLIENT)
public final class FabricRenderRegistrations {
    private static IEventBus installedBus;
    private static boolean dimensionsAttempted;
    private FabricRenderRegistrations() {}
    public static synchronized void install(IEventBus bus) {
        Objects.requireNonNull(bus, "bus"); if (installedBus == bus) return;
        if (installedBus != null) throw new IllegalStateException("Render registration bus already installed"); installedBus = bus;
    }
    private static IEventBus bus() {
        if (installedBus == null) throw new IllegalStateException("Render registration bus is not installed"); return installedBus;
    }
    public static void buffers(SequencedMap<RenderType, ByteBufferBuilder> buffers) { bus().post(new RegisterRenderBuffersEvent(buffers)); }
    public static synchronized void dimensions() {
        if (dimensionsAttempted) throw new IllegalStateException("Dimension effects initialization already attempted");
        dimensionsAttempted = true;
        // Both vanilla forType and Fabric's DimensionRenderingRegistry query this exact mutable map.
        // Direct put also preserves Neo's last-registration-wins behavior; FAPI registration uses putIfAbsent.
        bus().post(new RegisterDimensionSpecialEffectsEvent(DimensionEffectsAccess.stardewcraft$effects()));
    }
}
