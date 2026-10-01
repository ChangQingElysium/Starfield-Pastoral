package com.stardew.craft.port.internal.network;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.entity.player.Player;

/**
 * Client-only half of the payload layer. Only referenced from code paths that run on the logical
 * client (receiving a clientbound payload, {@code PacketDistributor.sendToServer}), so the JVM never
 * loads it on a dedicated server.
 */
final class PortClientNetwork {
    private PortClientNetwork() {
    }

    static RegistryAccess registryAccess() {
        ClientPacketListener listener = Minecraft.getInstance().getConnection();
        return listener != null ? listener.registryAccess() : PortNetwork.builtinAccess();
    }

    static Player localPlayer() {
        return Minecraft.getInstance().player;
    }

    static void send(Packet<?> packet) {
        ClientPacketListener listener = Minecraft.getInstance().getConnection();
        if (listener == null) {
            throw new IllegalStateException("Cannot send serverbound payloads while not connected to a server");
        }
        listener.send(packet);
    }
}
