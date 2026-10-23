package com.stardew.craft.platform.fabric.event;

import net.minecraft.server.MinecraftServer;

public final class ServerStoppedEvent extends ServerEvent {
    public ServerStoppedEvent(MinecraftServer server) { super(server); }
}
