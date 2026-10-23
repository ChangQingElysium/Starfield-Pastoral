package com.stardew.craft.platform.fabric.network;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Bounded, versioned configuration negotiation; Fabric channel announcements contain no versions. */
final class ProtocolManifest {
    private static final int MAX_ENTRIES = 1024;
    private static final int MAX_TEXT_LENGTH = 256;

    record Key(ConnectionProtocol protocol, PacketFlow flow, ResourceLocation id) {}

    record Entry(ResourceLocation id, ConnectionProtocol protocol, PacketFlow flow,
            String version, boolean optional) {
        Entry {
            Objects.requireNonNull(id, "Payload id");
            Objects.requireNonNull(protocol, "Payload protocol");
            Objects.requireNonNull(flow, "Payload flow");
            Objects.requireNonNull(version, "Payload version");
            if (id.toString().length() > MAX_TEXT_LENGTH || version.isBlank()
                    || version.length() > MAX_TEXT_LENGTH) {
                throw new IllegalArgumentException("Invalid payload manifest text length");
            }
            if (protocol != ConnectionProtocol.PLAY && protocol != ConnectionProtocol.CONFIGURATION) {
                throw new IllegalArgumentException("Only play and configuration payloads may be negotiated");
            }
        }

        Key key() { return new Key(protocol, flow, id); }
    }

    record Offer(long nonce, List<Entry> entries) implements CustomPacketPayload {
        static final Type<Offer> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
                "stardewcraft", "fabric_protocol_offer_v1"));
        static final StreamCodec<FriendlyByteBuf, Offer> CODEC = StreamCodec.of(
                (buffer, payload) -> { buffer.writeLong(payload.nonce); writeEntries(buffer, payload.entries); },
                buffer -> new Offer(buffer.readLong(), readEntries(buffer)));

        Offer { entries = checked(entries); }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    record Ack(long nonce, List<Entry> entries) implements CustomPacketPayload {
        static final Type<Ack> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
                "stardewcraft", "fabric_protocol_ack_v1"));
        static final StreamCodec<FriendlyByteBuf, Ack> CODEC = StreamCodec.of(
                (buffer, payload) -> { buffer.writeLong(payload.nonce); writeEntries(buffer, payload.entries); },
                buffer -> new Ack(buffer.readLong(), readEntries(buffer)));

        Ack { entries = checked(entries); }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    private static List<Entry> checked(List<Entry> entries) {
        List<Entry> copy = List.copyOf(entries);
        if (copy.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("Too many payload manifest entries");
        }
        HashSet<Key> keys = new HashSet<>();
        for (Entry entry : copy) {
            if (!keys.add(entry.key())) {
                throw new IllegalArgumentException("Duplicate payload manifest entry: " + entry.key());
            }
        }
        return copy;
    }

    private static List<Entry> readEntries(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > MAX_ENTRIES) {
            throw new IllegalArgumentException("Payload manifest count outside 0.." + MAX_ENTRIES);
        }
        List<Entry> entries = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            ResourceLocation id = ResourceLocation.parse(buffer.readUtf(MAX_TEXT_LENGTH));
            int flags = buffer.readUnsignedByte();
            if ((flags & ~7) != 0) {
                throw new IllegalArgumentException("Unknown payload manifest flags");
            }
            entries.add(new Entry(id,
                    (flags & 1) == 0 ? ConnectionProtocol.PLAY : ConnectionProtocol.CONFIGURATION,
                    (flags & 2) == 0 ? PacketFlow.SERVERBOUND : PacketFlow.CLIENTBOUND,
                    buffer.readUtf(MAX_TEXT_LENGTH), (flags & 4) != 0));
        }
        return checked(entries);
    }

    private static void writeEntries(FriendlyByteBuf buffer, List<Entry> entries) {
        buffer.writeVarInt(entries.size());
        for (Entry entry : entries) {
            buffer.writeUtf(entry.id.toString(), MAX_TEXT_LENGTH);
            int flags = (entry.protocol == ConnectionProtocol.CONFIGURATION ? 1 : 0)
                    | (entry.flow == PacketFlow.CLIENTBOUND ? 2 : 0) | (entry.optional ? 4 : 0);
            buffer.writeByte(flags);
            buffer.writeUtf(entry.version, MAX_TEXT_LENGTH);
        }
    }

    static Map<Key, Entry> indexed(List<Entry> entries) {
        Map<Key, Entry> result = new HashMap<>();
        for (Entry entry : checked(entries)) {
            result.put(entry.key(), entry);
        }
        return Map.copyOf(result);
    }

    /** Optional missing entries are accepted; a present incompatible version is rejected. */
    static String incompatibility(List<Entry> local, List<Entry> remote) {
        Map<Key, Entry> localIndex = indexed(local);
        Map<Key, Entry> remoteIndex = indexed(remote);
        for (Entry entry : local) {
            Entry peer = remoteIndex.get(entry.key());
            if (peer == null && !entry.optional) {
                return "Peer is missing required payload " + entry.key();
            }
            if (peer != null && !entry.version.equals(peer.version)) {
                return "Payload version mismatch for " + entry.key() + ": "
                        + entry.version + " / " + peer.version;
            }
        }
        for (Entry entry : remote) {
            if (!entry.optional && !localIndex.containsKey(entry.key())) {
                return "Local side is missing peer-required payload " + entry.key();
            }
        }
        return null;
    }

    private ProtocolManifest() {}
}
