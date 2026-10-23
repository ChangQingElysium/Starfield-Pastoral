package com.stardew.craft.platform.fabric.datamap;

import java.security.MessageDigest;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.Connection;

/** One active configuration snapshot per connection, independent of payload arrival order. */
final class DataMapAcknowledgements {
    private final Map<Connection, Pending> pending = new ConcurrentHashMap<>();

    void begin(Connection connection, long nonce, byte[] digest, long deadline, Runnable onTimeout) {
        if (pending.putIfAbsent(connection, new Pending(nonce, digest.clone(), deadline, onTimeout)) != null) {
            throw new IllegalStateException("Data map task is already pending");
        }
    }

    void acknowledge(Connection connection, DataMapTransfer.Ack ack) {
        Pending expected = pending.get(connection);
        if (expected == null || expected.nonce() != ack.nonce() || !MessageDigest.isEqual(expected.digest(), ack.digest())) {
            throw new IllegalStateException("Unsolicited, stale or incorrect data map acknowledgement");
        }
        if (!pending.remove(connection, expected)) throw new IllegalStateException("Data map acknowledgement raced task cancellation");
    }

    void expire(long now) {
        pending.forEach((connection, expected) -> {
            if (now - expected.deadline() >= 0 && pending.remove(connection, expected)) expected.onTimeout().run();
        });
    }

    void forget(Connection connection) { pending.remove(connection); }

    private record Pending(long nonce, byte[] digest, long deadline, Runnable onTimeout) {}
}
