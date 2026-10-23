package com.stardew.craft.platform.fabric.network;

import java.util.concurrent.TimeUnit;
import net.minecraft.resources.ResourceLocation;

/** One transfer and at most 16 MiB per connection. Only the receiving game thread mutates it. */
final class FragmentAssembler {
    static final long TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(30);
    private final int chunkBytes;
    private long completedSequence;
    private Active active;

    FragmentAssembler(int chunkBytes) {
        if (chunkBytes < 1 || chunkBytes > FabricFragmentation.CLIENTBOUND_CHUNK_BYTES) {
            throw new IllegalArgumentException("Invalid fragment chunk limit");
        }
        this.chunkBytes = chunkBytes;
    }

    /** Returns complete bytes once, and otherwise null while a bounded transfer is in progress. */
    Complete accept(FragmentPayload fragment, long nowNanos) {
        if (isExpired(nowNanos)) {
            active = null;
            throw new IllegalStateException("StardewCraft payload transfer timed out");
        }
        int expectedCount = (fragment.totalBytes() + chunkBytes - 1) / chunkBytes;
        int expectedLength = Math.min(chunkBytes, fragment.totalBytes() - fragment.index() * chunkBytes);
        byte[] bytes = fragment.bytes();
        if (fragment.count() != expectedCount || bytes.length != expectedLength) {
            throw new IllegalArgumentException("Fragment count or byte length does not match the bounded layout");
        }
        if (fragment.sequence() <= completedSequence) {
            throw new IllegalStateException("Replayed/out-of-order completed payload sequence");
        }
        if (active == null) {
            if (fragment.index() != 0) {
                throw new IllegalStateException("Payload transfer must start with fragment zero");
            }
            active = new Active(fragment, nowNanos);
        } else if (fragment.sequence() != active.sequence || fragment.nonce() != active.nonce
                || !fragment.originalType().equals(active.type) || fragment.count() != active.count
                || fragment.totalBytes() != active.bytes.length) {
            throw new IllegalStateException("Interleaved or inconsistent payload transfer");
        }
        if (fragment.index() != active.nextIndex) {
            throw new IllegalStateException("Duplicate or out-of-order payload fragment");
        }
        System.arraycopy(bytes, 0, active.bytes, fragment.index() * chunkBytes, bytes.length);
        active.nextIndex++;
        if (active.nextIndex != active.count) {
            return null;
        }
        Complete complete = new Complete(active.type, active.bytes);
        completedSequence = active.sequence;
        active = null;
        return complete;
    }

    boolean isExpired(long nowNanos) {
        return active != null && nowNanos - active.started >= TIMEOUT_NANOS;
    }

    int reservedBytes() { return active == null ? 0 : active.bytes.length; }

    record Complete(ResourceLocation type, byte[] bytes) {}

    private static final class Active {
        final long sequence;
        final long nonce;
        final ResourceLocation type;
        final int count;
        final long started;
        final byte[] bytes;
        int nextIndex;

        Active(FragmentPayload first, long nowNanos) {
            sequence = first.sequence();
            nonce = first.nonce();
            type = first.originalType();
            count = first.count();
            started = nowNanos;
            bytes = new byte[first.totalBytes()];
        }
    }
}
