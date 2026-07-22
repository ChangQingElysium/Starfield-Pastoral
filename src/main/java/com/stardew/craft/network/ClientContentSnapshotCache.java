package com.stardew.craft.network;

import java.util.Objects;
import java.util.function.LongFunction;

/** Server-thread-only cache scoped by owner identity. */
final class ClientContentSnapshotCache<K, V> {
    private K owner;
    private Entry<V> entry;
    private long generation;
    private boolean building;

    boolean contains(K owner) {
        return this.owner == Objects.requireNonNull(owner, "owner") && entry != null;
    }

    Entry<V> getOrBuild(K owner, LongFunction<V> builder) {
        rejectReentrantBuild();
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(builder, "builder");
        if (this.owner == owner && entry != null) {
            return entry;
        }
        return rebuild(owner, builder);
    }

    Entry<V> rebuild(K owner, LongFunction<V> builder) {
        rejectReentrantBuild();
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(builder, "builder");
        this.owner = null;
        entry = null;

        building = true;
        try {
            long nextGeneration = Math.addExact(generation, 1L);
            Entry<V> rebuilt = new Entry<>(nextGeneration, builder.apply(nextGeneration));
            this.owner = owner;
            entry = rebuilt;
            generation = nextGeneration;
            return rebuilt;
        } finally {
            building = false;
        }
    }

    void clear(K owner) {
        Objects.requireNonNull(owner, "owner");
        if (this.owner == owner) {
            this.owner = null;
            entry = null;
        }
    }

    private void rejectReentrantBuild() {
        if (building) {
            throw new IllegalStateException("content snapshot build already in progress");
        }
    }

    record Entry<V>(long generation, V value) {
        Entry {
            if (generation <= 0L) {
                throw new IllegalArgumentException("generation must be positive");
            }
            Objects.requireNonNull(value, "value");
        }
    }
}
