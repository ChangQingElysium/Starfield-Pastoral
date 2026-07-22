package com.stardew.craft.farm;

import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class TemporaryChunkLeaseTracker<L> {
    interface Backend<L> {
        boolean acquire(L level, ChunkPos chunk);

        void load(L level, ChunkPos chunk);

        void release(L level, ChunkPos chunk);
    }

    interface Lease extends AutoCloseable {
        @Override
        void close();
    }

    private static final Lease NO_OP_LEASE = () -> {};

    private final Backend<L> backend;
    private final IdentityHashMap<L, Map<ChunkPos, Entry<L>>> entriesByLevel = new IdentityHashMap<>();

    TemporaryChunkLeaseTracker(Backend<L> backend) {
        this.backend = Objects.requireNonNull(backend, "backend");
    }

    synchronized Lease acquire(L level, Collection<ChunkPos> chunks) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(chunks, "chunks");

        LinkedHashSet<ChunkPos> distinctChunks = new LinkedHashSet<>();
        for (ChunkPos chunk : chunks) {
            distinctChunks.add(Objects.requireNonNull(chunk, "chunk"));
        }
        if (distinctChunks.isEmpty()) return NO_OP_LEASE;

        Map<ChunkPos, Entry<L>> levelEntries = entriesByLevel.computeIfAbsent(level, ignored -> new HashMap<>());
        List<Acquisition<L>> acquisitions = new ArrayList<>(distinctChunks.size());
        try {
            for (ChunkPos chunk : distinctChunks) {
                Entry<L> entry = levelEntries.get(chunk);
                if (entry == null) {
                    entry = new Entry<>(level, chunk, backend.acquire(level, chunk));
                    levelEntries.put(chunk, entry);
                    acquisitions.add(new Acquisition<>(entry, true));
                } else {
                    int previousReferences = entry.references;
                    entry.references++;
                    acquisitions.add(new Acquisition<>(entry, previousReferences == 0));
                }
                if (!entry.loaded) {
                    backend.load(level, chunk);
                    entry.loaded = true;
                }
            }
        } catch (RuntimeException exception) {
            rollback(level, levelEntries, acquisitions, exception);
            throw exception;
        }

        List<LeaseEntry<L>> leaseEntries = acquisitions.stream()
                .map(acquisition -> new LeaseEntry<>(acquisition.entry, acquisition.entry.epoch))
                .toList();
        return new TrackedLease(leaseEntries);
    }

    synchronized void closeAll(L level) {
        Objects.requireNonNull(level, "level");
        Map<ChunkPos, Entry<L>> levelEntries = entriesByLevel.get(level);
        if (levelEntries != null) closeEntries(new ArrayList<>(levelEntries.values()));
    }

    synchronized void closeAll() {
        List<Entry<L>> entries = entriesByLevel.values().stream()
                .flatMap(levelEntries -> levelEntries.values().stream())
                .toList();
        closeEntries(entries);
    }

    private void rollback(L level, Map<ChunkPos, Entry<L>> levelEntries,
                          List<Acquisition<L>> acquisitions, RuntimeException failure) {
        for (int index = acquisitions.size() - 1; index >= 0; index--) {
            Acquisition<L> acquisition = acquisitions.get(index);
            Entry<L> entry = acquisition.entry;
            entry.references--;
            if (acquisition.releaseWhenUnusedOnRollback && entry.references == 0) {
                RuntimeException releaseFailure = releaseUnused(entry, failure);
                if (releaseFailure != failure) failure.addSuppressed(releaseFailure);
            }
        }
        if (levelEntries.isEmpty()) entriesByLevel.remove(level);
    }

    private synchronized void closeLease(List<LeaseEntry<L>> leaseEntries) {
        RuntimeException failure = null;
        for (LeaseEntry<L> leaseEntry : leaseEntries) {
            Entry<L> entry = leaseEntry.entry;
            if (!entry.active || entry.epoch != leaseEntry.epoch || --entry.references > 0) continue;
            failure = releaseUnused(entry, failure);
        }
        if (failure != null) throw failure;
    }

    private void closeEntries(Collection<Entry<L>> entries) {
        RuntimeException failure = null;
        for (Entry<L> entry : entries) {
            if (!entry.active) continue;
            entry.epoch++;
            entry.references = 0;
            failure = releaseUnused(entry, failure);
        }
        if (failure != null) throw failure;
    }

    private RuntimeException releaseUnused(Entry<L> entry, RuntimeException failure) {
        if (!entry.active || entry.references != 0) return failure;

        if (entry.owned) {
            try {
                backend.release(entry.level, entry.chunk);
            } catch (RuntimeException releaseFailure) {
                if (failure == null) return releaseFailure;
                failure.addSuppressed(releaseFailure);
                return failure;
            }
        }

        entry.active = false;
        Map<ChunkPos, Entry<L>> levelEntries = entriesByLevel.get(entry.level);
        if (levelEntries != null) {
            levelEntries.remove(entry.chunk, entry);
            if (levelEntries.isEmpty()) entriesByLevel.remove(entry.level);
        }
        return failure;
    }

    private record Acquisition<L>(Entry<L> entry, boolean releaseWhenUnusedOnRollback) {}

    private record LeaseEntry<L>(Entry<L> entry, long epoch) {}

    private static final class Entry<L> {
        private final L level;
        private final ChunkPos chunk;
        private final boolean owned;
        private int references = 1;
        private boolean active = true;
        private boolean loaded;
        private long epoch;

        private Entry(L level, ChunkPos chunk, boolean owned) {
            this.level = level;
            this.chunk = chunk;
            this.owned = owned;
        }
    }

    private final class TrackedLease implements Lease {
        private final List<LeaseEntry<L>> entries;
        private boolean closed;

        private TrackedLease(List<LeaseEntry<L>> entries) {
            this.entries = List.copyOf(entries);
        }

        @Override
        public synchronized void close() {
            if (closed) return;
            closed = true;
            closeLease(entries);
        }
    }
}
