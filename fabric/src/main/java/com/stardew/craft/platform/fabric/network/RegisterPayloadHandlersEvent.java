package com.stardew.craft.platform.fabric.network;

import com.stardew.craft.platform.fabric.event.ModBusEvent;

/** Payload registration is called explicitly from the Fabric common initializer. */
public final class RegisterPayloadHandlersEvent extends com.stardew.craft.platform.fabric.event.Event implements ModBusEvent {
    public PayloadRegistrar registrar(String version) {
        return new PayloadRegistrar(version, false);
    }
}
