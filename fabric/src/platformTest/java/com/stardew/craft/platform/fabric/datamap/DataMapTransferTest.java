package com.stardew.craft.platform.fabric.datamap;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.Unpooled;
import java.util.Arrays;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

class DataMapTransferTest {
    @Test
    void full16MiBSnapshotRoundTripsThroughBoundedConfigurationPackets() {
        byte[] original = new byte[DataMapSnapshot.MAX_BYTES];
        Arrays.fill(original, (byte) 37);
        original[original.length - 1] = 19;
        var chunks = DataMapTransfer.split(1234, original);
        assertEquals(512, chunks.size());
        var assembly = new DataMapTransfer.Assembly(chunks.getFirst());
        byte[] complete = null;
        for (var chunk : chunks) {
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                DataMapTransfer.Chunk.CODEC.encode(buffer, chunk);
                assertTrue(buffer.readableBytes() < 33 * 1024);
                complete = assembly.accept(DataMapTransfer.Chunk.CODEC.decode(buffer));
                assertFalse(buffer.isReadable());
            } finally { buffer.release(); }
        }
        assertArrayEquals(original, complete);
    }

    @Test
    void malformedOversizedDuplicateOutOfOrderAndCrossTransferChunksFail() {
        var chunks = DataMapTransfer.split(1, new byte[DataMapTransfer.CHUNK_BYTES + 1]);
        assertThrows(IllegalArgumentException.class, () -> new DataMapTransfer.Chunk(1, DataMapSnapshot.MAX_BYTES + 1, 0, new byte[1]));
        assertThrows(IllegalArgumentException.class, () -> new DataMapTransfer.Chunk(1, 1, 0, new byte[2]));
        assertThrows(IllegalArgumentException.class, () -> new DataMapTransfer.Assembly(chunks.getLast()));
        var assembly = new DataMapTransfer.Assembly(chunks.getFirst());
        assertThrows(IllegalArgumentException.class, () -> assembly.accept(chunks.getLast()));
        assertNull(assembly.accept(chunks.getFirst()));
        assertThrows(IllegalArgumentException.class, () -> assembly.accept(chunks.getFirst()));
        var other = new DataMapTransfer.Chunk(2, chunks.getLast().totalBytes(), 1, chunks.getLast().data());
        assertThrows(IllegalArgumentException.class, () -> assembly.accept(other));
    }

    @Test
    void acknowledgementRetainsNonceAndSha256AndRejectsUnboundedDigests() {
        byte[] snapshot = {1, 2, 3};
        var ack = new DataMapTransfer.Ack(55, DataMapSync.digest(snapshot));
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            DataMapTransfer.Ack.CODEC.encode(buffer, ack);
            assertTrue(buffer.readableBytes() < 64);
            var decoded = DataMapTransfer.Ack.CODEC.decode(buffer);
            assertEquals(55, decoded.nonce());
            assertArrayEquals(ack.digest(), decoded.digest());
            assertFalse(buffer.isReadable());
        } finally { buffer.release(); }
        assertThrows(IllegalArgumentException.class, () -> new DataMapTransfer.Ack(1, new byte[33]));
    }
}
