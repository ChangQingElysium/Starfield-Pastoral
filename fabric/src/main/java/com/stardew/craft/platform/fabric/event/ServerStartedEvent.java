package com.stardew.craft.platform.fabric.event;

import net.minecraft.server.MinecraftServer;

public final class ServerStartedEvent extends ServerEvent {
    public ServerStartedEvent(MinecraftServer server) { super(server); }
}
