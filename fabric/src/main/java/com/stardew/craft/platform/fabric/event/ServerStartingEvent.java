package com.stardew.craft.platform.fabric.event;

import net.minecraft.server.MinecraftServer;

/** Fabric publishes this after setupServer, immediately before ServerStartedEvent. */
public final class ServerStartingEvent extends ServerEvent {
    public ServerStartingEvent(MinecraftServer server) { super(server); }
}
