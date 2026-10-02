package com.stardew.craft.port.net.minecraft.network.protocol.common;

import com.stardew.craft.port.internal.network.PortNetwork;
import com.stardew.craft.port.net.minecraft.network.RegistryFriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.Packet;

/**
 * View of a 1.21.1 {@code ClientboundCustomPayloadPacket}. On Forge 1.20.1 payloads are carried by
 * vanilla {@code net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket}s of the
 * Stardew channel; {@link #unwrap(Packet)} recovers the payload from such a packet (used by game
 * tests that intercept {@code ServerGamePacketListenerImpl#send}).
 */
public record ClientboundCustomPayloadPacket(CustomPacketPayload payload) {
    /** Payload id followed by the payload's own codec, like vanilla's gameplay codec. */
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundCustomPayloadPacket> GAMEPLAY_STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ClientboundCustomPayloadPacket decode(RegistryFriendlyByteBuf buffer) {
            return new ClientboundCustomPayloadPacket(PortNetwork.decodeWithId(buffer));
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, ClientboundCustomPayloadPacket packet) {
            PortNetwork.encodeWithId(buffer, packet.payload());
        }
    };

    /**
     * A {@link ClientboundCustomPayloadPacket} view when {@code packet} carries a Stardew payload,
     * otherwise {@code packet} itself (also for non-final fragments of a split payload). Typed as
     * Object so call sites keep their {@code unwrap(packet) instanceof ClientboundCustomPayloadPacket custom}
     * pattern on Java 17.
     */
    public static Object unwrap(Packet<?> packet) {
        CustomPacketPayload payload = PortNetwork.decodeClientbound(packet);
        return payload == null ? packet : new ClientboundCustomPayloadPacket(payload);
    }
}
