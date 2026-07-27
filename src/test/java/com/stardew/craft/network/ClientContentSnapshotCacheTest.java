package com.stardew.craft.network;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientContentSnapshotCacheTest {
    @Test
    void sameOwnerReusesSnapshotAndExplicitRebuildRefreshesIt() {
        ClientContentSnapshotCache<Object, String> cache = new ClientContentSnapshotCache<>();
        Object owner = new Object();
        AtomicInteger builds = new AtomicInteger();

        var first = cache.getOrBuild(owner, generation -> "snapshot-" + builds.incrementAndGet());
        var second = cache.getOrBuild(owner, generation -> "unexpected-" + builds.incrementAndGet());
        var rebuilt = cache.rebuild(owner, generation -> "snapshot-" + builds.incrementAndGet());

        assertSame(first, second);
        assertEquals("snapshot-1", first.value());
        assertEquals("snapshot-2", rebuilt.value());
        assertEquals(2L, rebuilt.generation());
        assertEquals(2, builds.get());
    }

    @Test
    void ownerUsesIdentityAndClearOnlyAffectsMatchingServer() {
        ClientContentSnapshotCache<EqualOwner, String> cache = new ClientContentSnapshotCache<>();
        EqualOwner firstOwner = new EqualOwner();
        EqualOwner secondOwner = new EqualOwner();

        cache.getOrBuild(firstOwner, generation -> "first");
        assertTrue(cache.contains(firstOwner));
        assertFalse(cache.contains(secondOwner));
        cache.clear(secondOwner);
        assertTrue(cache.contains(firstOwner));
        cache.clear(firstOwner);
        assertFalse(cache.contains(firstOwner));
    }

    @Test
    void failedOrRecursiveBuildDoesNotPublishPartialSnapshot() {
        ClientContentSnapshotCache<Object, String> cache = new ClientContentSnapshotCache<>();
        Object owner = new Object();

        assertThrows(IllegalStateException.class, () -> cache.getOrBuild(owner,
                generation -> cache.getOrBuild(owner, nested -> "nested").value()));
        assertFalse(cache.contains(owner));

        var retry = cache.getOrBuild(owner, generation -> "retry-" + generation);
        assertEquals("retry-1", retry.value());
    }

    @Test
    void failedRefreshKeepsTheLastCompleteSnapshot() {
        ClientContentSnapshotCache<Object, String> cache = new ClientContentSnapshotCache<>();
        Object owner = new Object();
        var committed = cache.getOrBuild(owner, generation -> "committed-" + generation);

        assertThrows(IllegalStateException.class,
                () -> cache.rebuild(owner, generation -> {
                    throw new IllegalStateException("candidate failed");
                }));

        assertTrue(cache.contains(owner));
        assertSame(committed, cache.getOrBuild(owner, generation -> "unexpected"));
        var replacement = cache.rebuild(owner, generation -> "replacement-" + generation);
        assertEquals(2L, replacement.generation());
        assertEquals("replacement-2", replacement.value());
    }

    private static final class EqualOwner {
        @Override
        public boolean equals(Object obj) {
            return obj instanceof EqualOwner;
        }

        @Override
        public int hashCode() {
            return 1;
        }
    }
}
