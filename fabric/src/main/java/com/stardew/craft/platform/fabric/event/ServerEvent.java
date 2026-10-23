package com.stardew.craft.platform.fabric.event;

import net.minecraft.server.MinecraftServer;

public abstract class ServerEvent extends Event {
    private final MinecraftServer server;

    protected ServerEvent(MinecraftServer server) {
        this.server = server;
    }

    public final MinecraftServer getServer() {
        return server;
    }
}
