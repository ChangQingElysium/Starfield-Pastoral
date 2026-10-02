package com.stardew.craft.port.net.neoforged.neoforge.network.event;

import com.stardew.craft.port.internal.network.PortPayloadRegistry;
import com.stardew.craft.port.net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.event.IModBusEvent;

/**
 * NeoForge payload registration event. On Forge 1.20.1 it is posted once on the StardewCraft mod
 * bus during FMLCommonSetupEvent (before the Forge network registry locks) by
 * {@code PortNetworkBootstrap}; it fires identically on client and dedicated server.
 */
public class RegisterPayloadHandlersEvent extends Event implements IModBusEvent {
    private final PortPayloadRegistry.Builder sink;

    public RegisterPayloadHandlersEvent(PortPayloadRegistry.Builder sink) {
        this.sink = sink;
    }

    public PayloadRegistrar registrar(String version) {
        return new PayloadRegistrar(this.sink, version);
    }
}
