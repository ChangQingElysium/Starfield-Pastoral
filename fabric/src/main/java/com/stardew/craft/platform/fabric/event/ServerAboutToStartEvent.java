package com.stardew.craft.platform.fabric.event;

import net.minecraft.server.MinecraftServer;

/** Published before initServer, and therefore before any dimension opens region files. */
public final class ServerAboutToStartEvent extends ServerEvent {
    public ServerAboutToStartEvent(MinecraftServer server) { super(server); }
}
