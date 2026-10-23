package com.stardew.craft.platform.fabric.server;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/** The running server for existing common services which have no event context. */
public final class FabricServerLifecycle {
    private static final ResourceLocation RELEASE_SERVER = ResourceLocation.fromNamespaceAndPath(
            "stardewcraft", "release_server_reference");
    private static volatile MinecraftServer currentServer;
    private static boolean installed;

    private FabricServerLifecycle() {}

    /** Install from the common initializer, before a server can start. */
    public static synchronized void install() {
        if (installed) return;
        ServerLifecycleEvents.SERVER_STARTING.register(server -> currentServer = server);
        // Neo posts ServerStoppedEvent before clearing its global reference. This bridge
        // installs before the GAME bridge, so registration order in the default phase would
        // clear it too early. An explicit later phase also covers subsequently added listeners.
        ServerLifecycleEvents.SERVER_STOPPED.addPhaseOrdering(Event.DEFAULT_PHASE, RELEASE_SERVER);
        ServerLifecycleEvents.SERVER_STOPPED.register(RELEASE_SERVER, server -> {
            if (currentServer == server) currentServer = null;
        });
        installed = true;
    }

    public static MinecraftServer getCurrentServer() {
        return currentServer;
    }
}
