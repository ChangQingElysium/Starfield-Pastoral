package com.stardew.craft.port.internal.network;

import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.Packet;

/** Public entry points used by the {@code PacketDistributor} shim. */
public final class PortDistribution {
    private PortDistribution() {
    }

    /** Encodes payloads into one clientbound packet (a bundle when more than one packet results). */
    public static Packet<?> clientbound(CustomPacketPayload payload, CustomPacketPayload... payloads) {
        return PortNetwork.clientboundPacket(payload, payloads);
    }

    /** Client -> server, in order. Must be called on the logical client. */
    public static void sendToServer(CustomPacketPayload payload, CustomPacketPayload... payloads) {
        PortNetwork.sendToServer(payload);
        for (CustomPacketPayload next : payloads) {
            PortNetwork.sendToServer(next);
        }
    }
}
