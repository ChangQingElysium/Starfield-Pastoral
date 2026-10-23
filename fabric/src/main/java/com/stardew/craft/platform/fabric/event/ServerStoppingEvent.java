package com.stardew.craft.platform.fabric.event;

import net.minecraft.server.MinecraftServer;

public final class ServerStoppingEvent extends ServerEvent {
    public ServerStoppingEvent(MinecraftServer server) { super(server); }
}
