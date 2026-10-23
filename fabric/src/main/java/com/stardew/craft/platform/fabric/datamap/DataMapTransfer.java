package com.stardew.craft.platform.fabric.datamap;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Bounded configuration packets, independent of the play-only fragmentation transport. */
public final class DataMapTransfer {
    public static final int CHUNK_BYTES = 32 * 1024;

    private DataMapTransfer() {}

    static List<Chunk> split(long nonce, byte[] snapshot) {
        if (snapshot.length == 0 || snapshot.length > DataMapSnapshot.MAX_BYTES) throw new IllegalArgumentException("Invalid data map snapshot size");
        List<Chunk> result = new ArrayList<>();
        for (int offset = 0, index = 0; offset < snapshot.length; offset += CHUNK_BYTES, index++) {
            result.add(new Chunk(nonce, snapshot.length, index,
                    Arrays.copyOfRange(snapshot, offset, Math.min(snapshot.length, offset + CHUNK_BYTES))));
        }
        return result;
    }

    public record Chunk(long nonce, int totalBytes, int index, byte[] data) implements CustomPacketPayload {
        public static final Type<Chunk> TYPE = new Type<>(ResourceLocation.parse("stardewcraft:fabric_data_map_chunk"));
        public static final StreamCodec<FriendlyByteBuf, Chunk> CODEC = new StreamCodec<>() {
            @Override public Chunk decode(FriendlyByteBuf buf) { return new Chunk(buf.readLong(), buf.readVarInt(), buf.readVarInt(), buf.readByteArray(CHUNK_BYTES)); }
            @Override public void encode(FriendlyByteBuf buf, Chunk chunk) {
                buf.writeLong(chunk.nonce); buf.writeVarInt(chunk.totalBytes); buf.writeVarInt(chunk.index); buf.writeByteArray(chunk.data);
            }
        };
        public Chunk {
            if (totalBytes < 1 || totalBytes > DataMapSnapshot.MAX_BYTES || index < 0
                    || index >= (totalBytes + CHUNK_BYTES - 1) / CHUNK_BYTES) throw new IllegalArgumentException("Invalid data map chunk header");
            int expected = Math.min(CHUNK_BYTES, totalBytes - index * CHUNK_BYTES);
            if (data.length != expected) throw new IllegalArgumentException("Invalid data map chunk length");
            data = data.clone();
        }
        @Override public byte[] data() { return data.clone(); }
        @Override public Type<Chunk> type() { return TYPE; }
    }

    public record Ack(long nonce, byte[] digest) implements CustomPacketPayload {
        public static final Type<Ack> TYPE = new Type<>(ResourceLocation.parse("stardewcraft:fabric_data_map_ack"));
        public static final StreamCodec<FriendlyByteBuf, Ack> CODEC = new StreamCodec<>() {
            @Override public Ack decode(FriendlyByteBuf buf) { return new Ack(buf.readLong(), buf.readByteArray(32)); }
            @Override public void encode(FriendlyByteBuf buf, Ack ack) { buf.writeLong(ack.nonce); buf.writeByteArray(ack.digest); }
        };
        public Ack {
            if (digest.length != 32) throw new IllegalArgumentException("Expected SHA-256 data map acknowledgement");
            digest = digest.clone();
        }
        @Override public byte[] digest() { return digest.clone(); }
        @Override public Type<Ack> type() { return TYPE; }
    }

    static final class Assembly {
        final long nonce;
        final int total;
        private final ByteArrayOutputStream bytes;
        private int nextIndex;

        Assembly(Chunk first) {
            if (first.index() != 0) throw new IllegalArgumentException("Data map transfer must start at chunk 0");
            nonce = first.nonce();
            total = first.totalBytes();
            bytes = new ByteArrayOutputStream(Math.min(CHUNK_BYTES, total));
        }

        byte[] accept(Chunk chunk) {
            if (chunk.nonce() != nonce || chunk.totalBytes() != total || chunk.index() != nextIndex) {
                throw new IllegalArgumentException("Stale, duplicate or unordered data map chunk");
            }
            bytes.writeBytes(chunk.data);
            nextIndex++;
            return bytes.size() == total ? bytes.toByteArray() : null;
        }
    }
}
