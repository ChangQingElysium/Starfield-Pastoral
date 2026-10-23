package com.stardew.craft.platform.fabric.network;

import java.util.Objects;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Registers real Fabric payload codecs and receivers while keeping the existing call shape. */
public final class PayloadRegistrar {
    private final String version;
    private final boolean optional;

    PayloadRegistrar(String version, boolean optional) {
        this.version = Objects.requireNonNull(version, "Payload protocol version");
        if (version.isBlank() || version.length() > 256) {
            throw new IllegalArgumentException("Payload protocol version must contain 1..256 characters");
        }
        this.optional = optional;
    }

    public PayloadRegistrar optional() {
        return new PayloadRegistrar(version, true);
    }

    public PayloadRegistrar versioned(String version) {
        return new PayloadRegistrar(version, optional);
    }

    public <T extends CustomPacketPayload> PayloadRegistrar playToServer(
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            IPayloadHandler<T> handler) {
        FabricNetwork.registerPlay(type, codec, handler, PacketFlow.SERVERBOUND, version, optional);
        return this;
    }

    public <T extends CustomPacketPayload> PayloadRegistrar playToClient(
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            IPayloadHandler<T> handler) {
        FabricNetwork.registerPlay(type, codec, handler, PacketFlow.CLIENTBOUND, version, optional);
        return this;
    }

    public <T extends CustomPacketPayload> PayloadRegistrar playBidirectional(
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            IPayloadHandler<T> handler) {
        playToServer(type, codec, handler);
        playToClient(type, codec, handler);
        return this;
    }

    public <T extends CustomPacketPayload> PayloadRegistrar configurationToServer(
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super FriendlyByteBuf, T> codec,
            IPayloadHandler<T> handler) {
        FabricNetwork.registerConfiguration(type, codec, handler, PacketFlow.SERVERBOUND, version, optional);
        return this;
    }

    public <T extends CustomPacketPayload> PayloadRegistrar configurationToClient(
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super FriendlyByteBuf, T> codec,
            IPayloadHandler<T> handler) {
        FabricNetwork.registerConfiguration(type, codec, handler, PacketFlow.CLIENTBOUND, version, optional);
        return this;
    }

    public <T extends CustomPacketPayload> PayloadRegistrar configurationBidirectional(
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super FriendlyByteBuf, T> codec,
            IPayloadHandler<T> handler) {
        configurationToServer(type, codec, handler);
        configurationToClient(type, codec, handler);
        return this;
    }
}
