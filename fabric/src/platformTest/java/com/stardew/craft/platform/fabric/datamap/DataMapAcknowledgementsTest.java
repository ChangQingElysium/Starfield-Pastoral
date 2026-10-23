package com.stardew.craft.platform.fabric.datamap;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import org.junit.jupiter.api.Test;

class DataMapAcknowledgementsTest {
    @Test
    void onlyTheExpectedConnectionNonceAndDigestCanCompleteOnce() {
        var tracker = new DataMapAcknowledgements();
        var first = new Connection(PacketFlow.SERVERBOUND);
        var other = new Connection(PacketFlow.SERVERBOUND);
        byte[] digest = DataMapSync.digest(new byte[]{1});
        var ack = new DataMapTransfer.Ack(10, digest);
        tracker.begin(first, 10, digest, 100, () -> fail("completed task timed out"));
        assertThrows(IllegalStateException.class, () -> tracker.begin(first, 11, digest, 100, () -> {}));
        assertThrows(IllegalStateException.class, () -> tracker.acknowledge(other, ack));
        assertThrows(IllegalStateException.class, () -> tracker.acknowledge(first, new DataMapTransfer.Ack(9, digest)));
        assertThrows(IllegalStateException.class, () -> tracker.acknowledge(first, new DataMapTransfer.Ack(10, new byte[32])));
        tracker.acknowledge(first, ack);
        assertThrows(IllegalStateException.class, () -> tracker.acknowledge(first, ack));
        tracker.expire(101);
    }

    @Test
    void reconfigurationAndTimeoutInvalidateOldAcknowledgements() {
        var tracker = new DataMapAcknowledgements();
        var connection = new Connection(PacketFlow.SERVERBOUND);
        byte[] digest = new byte[32];
        var timeouts = new AtomicInteger();
        tracker.begin(connection, 1, digest, 100, timeouts::incrementAndGet);
        tracker.forget(connection);
        tracker.begin(connection, 2, digest, 200, timeouts::incrementAndGet);
        assertThrows(IllegalStateException.class, () -> tracker.acknowledge(connection, new DataMapTransfer.Ack(1, digest)));
        tracker.expire(199);
        assertEquals(0, timeouts.get());
        tracker.expire(200);
        tracker.expire(201);
        assertEquals(1, timeouts.get());
        assertThrows(IllegalStateException.class, () -> tracker.acknowledge(connection, new DataMapTransfer.Ack(2, digest)));
    }
}
