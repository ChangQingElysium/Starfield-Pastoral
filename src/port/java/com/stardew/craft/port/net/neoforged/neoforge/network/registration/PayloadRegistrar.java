package com.stardew.craft.port.net.neoforged.neoforge.network.registration;

import com.stardew.craft.port.internal.network.PortPayloadRegistry;
import com.stardew.craft.port.net.minecraft.network.RegistryFriendlyByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.minecraft.network.FriendlyByteBuf;

/**
 * NeoForge 21.1 payload registrar on Forge 1.20.1. Every registration lands in
 * {@link PortPayloadRegistry}, which assigns wire ids by sorted payload id once registration ends,
 * so client and server agree regardless of listener order.
 *
 * <p>Versions and the optional flag are folded into the Stardew channel's protocol version: any
 * mismatch rejects the connection during the Forge login handshake (NeoForge rejects per payload).
 */
public class PayloadRegistrar {
    private final PortPayloadRegistry.Builder sink;
    private final String version;
    private final boolean optional;
    private final HandlerThread thread;

    public PayloadRegistrar(PortPayloadRegistry.Builder sink, String version) {
        this(sink, version, false, HandlerThread.MAIN);
    }

    private PayloadRegistrar(PortPayloadRegistry.Builder sink, String version, boolean optional, HandlerThread thread) {
        this.sink = sink;
        this.version = version;
        this.optional = optional;
        this.thread = thread;
    }

    public <T extends CustomPacketPayload> PayloadRegistrar playToClient(
            CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec, IPayloadHandler<T> handler) {
        this.sink.register(PortPayloadRegistry.Phase.PLAY, type, codec, handler, null, this.version, this.optional, this.thread);
        return this;
    }

    public <T extends CustomPacketPayload> PayloadRegistrar playToServer(
            CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec, IPayloadHandler<T> handler) {
        this.sink.register(PortPayloadRegistry.Phase.PLAY, type, codec, null, handler, this.version, this.optional, this.thread);
        return this;
    }

    public <T extends CustomPacketPayload> PayloadRegistrar playBidirectional(
            CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec, IPayloadHandler<T> handler) {
        this.sink.register(PortPayloadRegistry.Phase.PLAY, type, codec, handler, handler, this.version, this.optional, this.thread);
        return this;
    }

    public <T extends CustomPacketPayload> PayloadRegistrar configurationToClient(
            CustomPacketPayload.Type<T> type, StreamCodec<? super FriendlyByteBuf, T> codec, IPayloadHandler<T> handler) {
        this.sink.register(PortPayloadRegistry.Phase.CONFIGURATION, type, codec, handler, null, this.version, this.optional, this.thread);
        return this;
    }

    public <T extends CustomPacketPayload> PayloadRegistrar configurationToServer(
            CustomPacketPayload.Type<T> type, StreamCodec<? super FriendlyByteBuf, T> codec, IPayloadHandler<T> handler) {
        this.sink.register(PortPayloadRegistry.Phase.CONFIGURATION, type, codec, null, handler, this.version, this.optional, this.thread);
        return this;
    }

    public <T extends CustomPacketPayload> PayloadRegistrar configurationBidirectional(
            CustomPacketPayload.Type<T> type, StreamCodec<? super FriendlyByteBuf, T> codec, IPayloadHandler<T> handler) {
        this.sink.register(PortPayloadRegistry.Phase.CONFIGURATION, type, codec, handler, handler, this.version, this.optional, this.thread);
        return this;
    }

    public <T extends CustomPacketPayload> PayloadRegistrar commonToClient(
            CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec, IPayloadHandler<T> handler) {
        this.sink.register(PortPayloadRegistry.Phase.CONFIGURATION, type, codec, handler, null, this.version, this.optional, this.thread);
        return this.playToClient(type, codec, handler);
    }

    public <T extends CustomPacketPayload> PayloadRegistrar commonToServer(
            CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec, IPayloadHandler<T> handler) {
        this.sink.register(PortPayloadRegistry.Phase.CONFIGURATION, type, codec, null, handler, this.version, this.optional, this.thread);
        return this.playToServer(type, codec, handler);
    }

    public <T extends CustomPacketPayload> PayloadRegistrar commonBidirectional(
            CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec, IPayloadHandler<T> handler) {
        this.sink.register(PortPayloadRegistry.Phase.CONFIGURATION, type, codec, handler, handler, this.version, this.optional, this.thread);
        return this.playBidirectional(type, codec, handler);
    }

    public PayloadRegistrar executesOn(HandlerThread thread) {
        return new PayloadRegistrar(this.sink, this.version, this.optional, thread);
    }

    public PayloadRegistrar versioned(String version) {
        return new PayloadRegistrar(this.sink, version, this.optional, this.thread);
    }

    public PayloadRegistrar optional() {
        return new PayloadRegistrar(this.sink, this.version, true, this.thread);
    }
}
