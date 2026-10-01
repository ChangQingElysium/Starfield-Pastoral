package com.stardew.craft.port;

import net.minecraft.client.Minecraft;

/** PORT(1.20.1): client-side handlers of {@link PortNetwork} messages (only loaded on the client). */
final class PortClientHandlers {
    private PortClientHandlers() {}

    static void handle(PortDataMaps.SyncMessage message) {
        Minecraft minecraft = Minecraft.getInstance();
        // An integrated server shares the loaded data maps with its client.
        if (minecraft.isLocalServer() || minecraft.getConnection() == null) return;
        PortDataMaps.applySync(message, minecraft.getConnection().registryAccess());
    }

    static void handle(PortAuxLight.SyncMessage message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        PortAuxLight.applySync(minecraft.level, message);
    }
}
