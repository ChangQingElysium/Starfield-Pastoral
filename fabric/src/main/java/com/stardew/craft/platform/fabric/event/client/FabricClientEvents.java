package com.stardew.craft.platform.fabric.event.client;

import com.stardew.craft.platform.fabric.event.ClientTickEvent;
import com.stardew.craft.platform.fabric.event.FabricEvents;
import com.stardew.craft.platform.fabric.event.IEventBus;
import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/** Install from ClientModInitializer after collecting the client subscribers. */
@Environment(EnvType.CLIENT)
public final class FabricClientEvents {
    private static IEventBus installedBus;

    private FabricClientEvents() {}

    public static void install() {
        install(FabricEvents.GAME);
    }

    public static synchronized void install(IEventBus bus) {
        Objects.requireNonNull(bus, "bus");
        if (installedBus == bus) return;
        if (installedBus != null) throw new IllegalStateException("Client events already have an installed bus");
        // Fabric API 0.116.17's MinecraftClientMixin publishes these at the
        // HEAD and RETURN of Minecraft.tick, including ticks without a world.
        ClientTickEvents.START_CLIENT_TICK.register(client -> bus.post(new ClientTickEvent.Pre()));
        ClientTickEvents.END_CLIENT_TICK.register(client -> bus.post(new ClientTickEvent.Post()));
        installedBus = bus;
    }
}
