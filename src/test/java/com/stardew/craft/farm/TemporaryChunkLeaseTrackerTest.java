package com.stardew.craft.farm;

import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TemporaryChunkLeaseTrackerTest {
    @Test
    void overlappingLeasesLoadOnceAndReleaseAfterLastReference() {
        FakeBackend backend = new FakeBackend();
        TemporaryChunkLeaseTracker<Object> tracker = new TemporaryChunkLeaseTracker<>(backend);
        Object level = new Object();
        ChunkPos chunk = new ChunkPos(3, -2);

        var first = tracker.acquire(level, List.of(chunk, chunk));
        var second = tracker.acquire(level, List.of(chunk));
        assertEquals(List.of(chunk), backend.acquired);
        assertEquals(List.of(chunk), backend.loaded);

        first.close();
        assertEquals(List.of(), backend.released);
        second.close();
        assertEquals(List.of(chunk), backend.released);
    }

    @Test
    void existingExternalTicketIsNeverReleasedByTracker() {
        FakeBackend backend = new FakeBackend();
        TemporaryChunkLeaseTracker<Object> tracker = new TemporaryChunkLeaseTracker<>(backend);
        Object level = new Object();
        ChunkPos chunk = new ChunkPos(1, 1);
        backend.externallyForced.add(chunk);

        tracker.acquire(level, List.of(chunk)).close();

        assertEquals(List.of(chunk), backend.acquired);
        assertEquals(List.of(chunk), backend.loaded);
        assertEquals(List.of(), backend.released);
    }

    @Test
    void failedLoadRollsBackOwnedTickets() {
        FakeBackend backend = new FakeBackend();
        TemporaryChunkLeaseTracker<Object> tracker = new TemporaryChunkLeaseTracker<>(backend);
        Object level = new Object();
        ChunkPos first = new ChunkPos(0, 0);
        ChunkPos failing = new ChunkPos(1, 0);
        backend.failLoad = failing;

        assertThrows(IllegalStateException.class, () -> tracker.acquire(level, List.of(first, failing)));

        assertEquals(List.of(failing, first), backend.released);
    }

    @Test
    void closeAllInvalidatesOutstandingLeasesWithoutDoubleRelease() {
        FakeBackend backend = new FakeBackend();
        TemporaryChunkLeaseTracker<Object> tracker = new TemporaryChunkLeaseTracker<>(backend);
        Object level = new Object();
        ChunkPos chunk = new ChunkPos(4, 5);
        var lease = tracker.acquire(level, List.of(chunk));

        tracker.closeAll(level);
        lease.close();

        assertEquals(List.of(chunk), backend.released);
    }

    private static final class FakeBackend implements TemporaryChunkLeaseTracker.Backend<Object> {
        private final Set<ChunkPos> externallyForced = new HashSet<>();
        private final List<ChunkPos> acquired = new ArrayList<>();
        private final List<ChunkPos> loaded = new ArrayList<>();
        private final List<ChunkPos> released = new ArrayList<>();
        private ChunkPos failLoad;

        @Override
        public boolean acquire(Object level, ChunkPos chunk) {
            acquired.add(chunk);
            return !externallyForced.contains(chunk);
        }

        @Override
        public void load(Object level, ChunkPos chunk) {
            loaded.add(chunk);
            if (chunk.equals(failLoad)) throw new IllegalStateException("load failed");
        }

        @Override
        public void release(Object level, ChunkPos chunk) {
            released.add(chunk);
        }
    }
}
