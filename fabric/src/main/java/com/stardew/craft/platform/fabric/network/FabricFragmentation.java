package com.stardew.craft.platform.fabric.network;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Transparent play transport for the existing 16 MiB content-sync codec. */
public final class FabricFragmentation {
    public static final int MAX_PAYLOAD_BYTES = 16 * 1024 * 1024;
    // Leave space for the type id, transfer metadata, VarInts and the packet's own payload id.
    static final int CLIENTBOUND_CHUNK_BYTES = 1024 * 1024 - 1024;
    static final int SERVERBOUND_CHUNK_BYTES = 32 * 1024 - 1024;
    static final int MAX_FRAGMENT_COUNT = (MAX_PAYLOAD_BYTES + SERVERBOUND_CHUNK_BYTES - 1)
            / SERVERBOUND_CHUNK_BYTES;
    private static final AtomicLong SEQUENCE = new AtomicLong();
    private static final Map<Connection, Inbound> INBOUND = new ConcurrentHashMap<>();

    static void initialize(PayloadRegistrar registrar) {
        registrar.playBidirectional(FragmentPayload.TYPE, FragmentPayload.CODEC, FabricFragmentation::receive);
        ServerTickEvents.END_SERVER_TICK.register(server -> tick(PacketFlow.SERVERBOUND));
    }

    /** Run on the sending game thread so transfer sequence and wire ordering agree. */
    public static List<CustomPacketPayload> prepare(CustomPacketPayload payload, RegistryAccess registries,
            PacketFlow flow) {
        if (payload instanceof FragmentPayload) {
            throw new IllegalArgumentException("Transport fragments cannot be nested or sent by gameplay code");
        }
        FabricNetwork.Registration<?> registration = FabricNetwork.playRegistration(payload.type().id(), flow);
        return prepareWithCodec(payload, registration.codec(), registries, flow);
    }

    static List<CustomPacketPayload> prepareWithCodec(CustomPacketPayload payload, StreamCodec<?, ?> codec,
            RegistryAccess registries, PacketFlow flow) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(256, MAX_PAYLOAD_BYTES), registries);
        try {
            encode(codec, buffer, payload);
            int limit = flow == PacketFlow.CLIENTBOUND ? CLIENTBOUND_CHUNK_BYTES : SERVERBOUND_CHUNK_BYTES;
            if (buffer.readableBytes() <= limit) {
                return List.of(payload);
            }
            byte[] encoded = new byte[buffer.readableBytes()];
            buffer.readBytes(encoded);
            return split(payload.type().id(), encoded, flow, nextSequence(), nonzeroNonce());
        } catch (IndexOutOfBoundsException failure) {
            throw new EncoderException("StardewCraft payload exceeds the 16 MiB transport limit", failure);
        } finally {
            buffer.release();
        }
    }

    static List<CustomPacketPayload> split(net.minecraft.resources.ResourceLocation type, byte[] encoded,
            PacketFlow flow, long sequence, long nonce) {
        if (encoded.length < 1 || encoded.length > MAX_PAYLOAD_BYTES) {
            throw new EncoderException("StardewCraft payload must fit in 1..16 MiB");
        }
        int chunkBytes = flow == PacketFlow.CLIENTBOUND ? CLIENTBOUND_CHUNK_BYTES : SERVERBOUND_CHUNK_BYTES;
        int count = (encoded.length + chunkBytes - 1) / chunkBytes;
        List<CustomPacketPayload> fragments = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            int offset = index * chunkBytes;
            byte[] bytes = java.util.Arrays.copyOfRange(encoded, offset, Math.min(encoded.length, offset + chunkBytes));
            fragments.add(new FragmentPayload(sequence, nonce, type, index, count, encoded.length, bytes));
        }
        return List.copyOf(fragments);
    }

    @SuppressWarnings("unchecked")
    private static void encode(StreamCodec<?, ?> codec,
            RegistryFriendlyByteBuf buffer, CustomPacketPayload payload) {
        ((StreamCodec<RegistryFriendlyByteBuf, CustomPacketPayload>) codec).encode(buffer, payload);
    }

    private static void receive(FragmentPayload fragment, IPayloadContext context) {
        // Reject unknown/wrong-direction and recursively-fragmented ids before reserving memory.
        FabricNetwork.playRegistration(fragment.originalType(), context.flow());
        FragmentAssembler assembler = assembler(context);
        FragmentAssembler.Complete complete;
        try {
            complete = assembler.accept(fragment, System.nanoTime());
        } catch (RuntimeException failure) {
            INBOUND.remove(context.connection());
            throw failure;
        }
        if (complete == null) {
            return;
        }
        FabricNetwork.Registration<?> registration = FabricNetwork.playRegistration(complete.type(), context.flow());
        CustomPacketPayload decoded = decodePayload(complete, registration.codec(), context.player().registryAccess());
        FabricNetwork.dispatch(decoded, context);
    }

    /** Gets this connection's real bounded transfer state; also makes lifecycle isolation testable. */
    static FragmentAssembler assembler(IPayloadContext context) {
        Inbound state = INBOUND.computeIfAbsent(context.connection(), connection -> new Inbound(context,
                new FragmentAssembler(context.flow() == PacketFlow.CLIENTBOUND
                        ? CLIENTBOUND_CHUNK_BYTES : SERVERBOUND_CHUNK_BYTES)));
        if (state.context.flow() != context.flow()) {
            throw new IllegalStateException("Connection changed incoming payload direction");
        }
        return state.assembler;
    }

    static CustomPacketPayload decodePayload(FragmentAssembler.Complete complete, StreamCodec<?, ?> codec,
            RegistryAccess registries) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(complete.bytes()),
                registries);
        try {
            CustomPacketPayload decoded = decode(codec, buffer);
            if (buffer.isReadable() || !decoded.type().id().equals(complete.type())) {
                throw new DecoderException("Fragmented payload decoder left bytes or returned a different payload type");
            }
            return decoded;
        } finally {
            buffer.release();
        }
    }

    @SuppressWarnings("unchecked")
    private static CustomPacketPayload decode(StreamCodec<?, ?> codec, RegistryFriendlyByteBuf buffer) {
        return ((StreamCodec<RegistryFriendlyByteBuf, ? extends CustomPacketPayload>) codec).decode(buffer);
    }

    /** Expiry also runs when no more fragments arrive, preventing a partial-transfer memory leak. */
    public static void tick(PacketFlow incomingFlow) {
        long now = System.nanoTime();
        for (Map.Entry<Connection, Inbound> entry : INBOUND.entrySet()) {
            Inbound inbound = entry.getValue();
            if (inbound.context.flow() == incomingFlow && inbound.assembler.isExpired(now)
                    && INBOUND.remove(entry.getKey(), inbound)) {
                inbound.context.disconnect(FabricNetwork.protocolErrorReason());
            }
        }
    }

    static void forget(Connection connection) { INBOUND.remove(connection); }

    private static long nextSequence() {
        long sequence = SEQUENCE.incrementAndGet();
        if (sequence <= 0) {
            throw new IllegalStateException("Payload transport sequence exhausted");
        }
        return sequence;
    }

    private static long nonzeroNonce() {
        long nonce;
        do { nonce = ThreadLocalRandom.current().nextLong(); } while (nonce == 0);
        return nonce;
    }

    private record Inbound(IPayloadContext context, FragmentAssembler assembler) {}

    private FabricFragmentation() {}
}
