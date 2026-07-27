package com.stardew.craft.api.v1.internal.extension;

import com.stardew.craft.api.v1.extension.StardewExtensionPointSnapshot;
import com.stardew.craft.api.v1.extension.StardewExtensionFailure;
import com.stardew.craft.api.v1.extension.StardewExtensionIssue;
import com.stardew.craft.api.v1.extension.StardewExtensionLifecycle;
import com.stardew.craft.api.v1.extension.StardewExtensionRegistration;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Shared copy-on-write registry for ordered extension providers.
 *
 * <p>Domain APIs keep their strongly typed registration methods and dispatch semantics. This
 * class only centralizes identity, duplicate checks, stable ordering and diagnostic snapshots.
 */
public final class OrderedExtensionRegistry<T> {
    private static final long SLOW_INVOCATION_NANOS = 10_000_000L;
    private final ResourceLocation extensionPointId;
    private final Map<ResourceLocation, Entry<T>> registrations = new LinkedHashMap<>();
    private final Map<ResourceLocation, InvocationMetrics> metrics =
            new ConcurrentHashMap<>();
    private final List<StardewExtensionIssue> issues = new ArrayList<>();
    private StardewExtensionLifecycle lifecycle =
            StardewExtensionLifecycle.REGISTERING;
    private volatile Snapshot<T> snapshot;

    public OrderedExtensionRegistry(ResourceLocation extensionPointId) {
        this.extensionPointId = Objects.requireNonNull(
                extensionPointId, "extensionPointId");
        this.snapshot = new Snapshot<>(extensionPointId, 0L, List.of());
        if (ExtensionPointCatalog.register(
                extensionPointId,
                this::diagnosticSnapshot,
                this::freeze)) {
            freeze();
        }
    }

    public synchronized void register(
            ResourceLocation id,
            int priority,
            T extension
    ) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(extension, "extension");
        if (lifecycle != StardewExtensionLifecycle.REGISTERING) {
            reject(
                    id,
                    StardewExtensionIssue.Kind.LATE_REGISTRATION,
                    "Extension point " + extensionPointId
                            + " is frozen");
        }
        if (registrations.containsKey(id)) {
            reject(
                    id,
                    StardewExtensionIssue.Kind.DUPLICATE_ID,
                    "Duplicate extension " + id
                            + " for " + extensionPointId);
        }
        registrations.put(id, new Entry<>(id, priority, extension));
        metrics.put(id, new InvocationMetrics());
        ArrayList<Entry<T>> ordered = new ArrayList<>(registrations.values());
        ordered.sort(Comparator
                .comparingInt(Entry<T>::priority)
                .reversed()
                .thenComparing(entry -> entry.id().toString()));
        snapshot = new Snapshot<>(
                extensionPointId,
                snapshot.revision() + 1L,
                List.copyOf(ordered));
    }

    public synchronized void freeze() {
        if (lifecycle == StardewExtensionLifecycle.FROZEN) {
            return;
        }
        lifecycle = StardewExtensionLifecycle.FROZEN;
        snapshot = new Snapshot<>(
                extensionPointId,
                snapshot.revision() + 1L,
                snapshot.entries());
    }

    public List<Entry<T>> entries() {
        return snapshot.entries();
    }

    public Snapshot<T> snapshot() {
        return snapshot;
    }

    public <R> R invoke(
            Entry<T> entry,
            Function<T, R> invocation
    ) {
        Objects.requireNonNull(entry, "entry");
        Objects.requireNonNull(invocation, "invocation");
        InvocationMetrics invocationMetrics = metrics.get(entry.id());
        long started = System.nanoTime();
        try {
            return invocation.apply(entry.extension());
        } catch (RuntimeException | Error failure) {
            if (invocationMetrics != null) {
                invocationMetrics.recordFailure(failure);
            }
            throw failure;
        } finally {
            if (invocationMetrics != null) {
                invocationMetrics.recordDuration(
                        Math.max(0L, System.nanoTime() - started));
            }
        }
    }

    public void invokeVoid(
            Entry<T> entry,
            Consumer<T> invocation
    ) {
        invoke(entry, extension -> {
            invocation.accept(extension);
            return null;
        });
    }

    public void invokeCheckedVoid(
            Entry<T> entry,
            CheckedConsumer<T> invocation
    ) throws Exception {
        Objects.requireNonNull(entry, "entry");
        Objects.requireNonNull(invocation, "invocation");
        InvocationMetrics invocationMetrics = metrics.get(entry.id());
        long started = System.nanoTime();
        try {
            invocation.accept(entry.extension());
        } catch (Exception | Error failure) {
            if (invocationMetrics != null) {
                invocationMetrics.recordFailure(failure);
            }
            throw failure;
        } finally {
            if (invocationMetrics != null) {
                invocationMetrics.recordDuration(
                        Math.max(0L, System.nanoTime() - started));
            }
        }
    }

    public synchronized StardewExtensionPointSnapshot diagnosticSnapshot() {
        Snapshot<T> current = snapshot;
        return new StardewExtensionPointSnapshot(
                current.extensionPointId(),
                current.revision(),
                lifecycle,
                current.entries().stream()
                        .map(this::diagnosticRegistration)
                        .toList(),
                issues);
    }

    private void reject(
            ResourceLocation id,
            StardewExtensionIssue.Kind kind,
            String message
    ) {
        issues.add(new StardewExtensionIssue(
                id, kind, message, System.currentTimeMillis()));
        snapshot = new Snapshot<>(
                extensionPointId,
                snapshot.revision() + 1L,
                snapshot.entries());
        throw new IllegalStateException(message);
    }

    private StardewExtensionRegistration diagnosticRegistration(
            Entry<T> entry
    ) {
        InvocationMetrics current = metrics.get(entry.id());
        if (current == null) {
            return new StardewExtensionRegistration(
                    entry.id(), entry.priority());
        }
        return new StardewExtensionRegistration(
                entry.id(),
                entry.priority(),
                current.invocationCount.get(),
                current.failureCount.get(),
                current.slowInvocationCount.get(),
                current.totalNanos.get(),
                current.maxNanos.get(),
                Optional.ofNullable(current.lastFailure.get()));
    }

    public record Entry<T>(
            ResourceLocation id,
            int priority,
            T extension
    ) {
        public Entry {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(extension, "extension");
        }
    }

    public record Snapshot<T>(
            ResourceLocation extensionPointId,
            long revision,
            List<Entry<T>> entries
    ) {
        public Snapshot {
            Objects.requireNonNull(extensionPointId, "extensionPointId");
            entries = List.copyOf(entries);
        }
    }

    @FunctionalInterface
    public interface CheckedConsumer<T> {
        void accept(T extension) throws Exception;
    }

    private static final class InvocationMetrics {
        private final AtomicLong invocationCount = new AtomicLong();
        private final AtomicLong failureCount = new AtomicLong();
        private final AtomicLong slowInvocationCount = new AtomicLong();
        private final AtomicLong totalNanos = new AtomicLong();
        private final AtomicLong maxNanos = new AtomicLong();
        private final AtomicReference<
                StardewExtensionFailure> lastFailure =
                new AtomicReference<>();

        private void recordDuration(long durationNanos) {
            invocationCount.incrementAndGet();
            totalNanos.addAndGet(durationNanos);
            maxNanos.accumulateAndGet(durationNanos, Math::max);
            if (durationNanos >= SLOW_INVOCATION_NANOS) {
                slowInvocationCount.incrementAndGet();
            }
        }

        private void recordFailure(Throwable failure) {
            failureCount.incrementAndGet();
            lastFailure.set(new StardewExtensionFailure(
                    failure.getClass().getName(),
                    failure.getMessage(),
                    System.currentTimeMillis()));
        }
    }
}
