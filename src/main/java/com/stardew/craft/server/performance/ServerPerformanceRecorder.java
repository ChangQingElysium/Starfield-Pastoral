package com.stardew.craft.server.performance;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Objects;
import java.util.function.Supplier;

/** Server-thread-only rolling diagnostics with allocation-free record paths. */
public final class ServerPerformanceRecorder {
    private static final int TIMING_WINDOW_SIZE = 1_200;
    private static final RollingTimingWindow[] TIMINGS = Arrays.stream(PerformanceTiming.values())
            .map(ignored -> new RollingTimingWindow(TIMING_WINDOW_SIZE))
            .toArray(RollingTimingWindow[]::new);
    private static final long[] COUNTERS = new long[PerformanceCounter.values().length];
    private static boolean enabled;

    private ServerPerformanceRecorder() {}

    public static void record(PerformanceTiming timing, long nanoseconds) {
        Objects.requireNonNull(timing, "timing");
        if (!enabled) return;
        TIMINGS[timing.ordinal()].record(nanoseconds);
    }

    public static void increment(PerformanceCounter counter, long amount) {
        Objects.requireNonNull(counter, "counter");
        if (!enabled) return;
        if (amount <= 0L) return;
        int index = counter.ordinal();
        long current = COUNTERS[index];
        COUNTERS[index] = current > Long.MAX_VALUE - amount ? Long.MAX_VALUE : current + amount;
    }

    public static <T> T measure(PerformanceTiming timing, Supplier<T> operation) {
        Objects.requireNonNull(timing, "timing");
        Objects.requireNonNull(operation, "operation");
        if (!enabled) return operation.get();
        long startedAt = System.nanoTime();
        try {
            return operation.get();
        } finally {
            record(timing, System.nanoTime() - startedAt);
        }
    }

    public static void measure(PerformanceTiming timing, Runnable operation) {
        Objects.requireNonNull(timing, "timing");
        Objects.requireNonNull(operation, "operation");
        if (!enabled) {
            operation.run();
            return;
        }
        long startedAt = System.nanoTime();
        try {
            operation.run();
        } finally {
            record(timing, System.nanoTime() - startedAt);
        }
    }

    public static PerformanceSnapshot snapshot() {
        EnumMap<PerformanceTiming, TimingSummary> timingSnapshots = new EnumMap<>(PerformanceTiming.class);
        for (PerformanceTiming timing : PerformanceTiming.values()) {
            timingSnapshots.put(timing, TIMINGS[timing.ordinal()].snapshot());
        }
        EnumMap<PerformanceCounter, Long> counterSnapshots = new EnumMap<>(PerformanceCounter.class);
        for (PerformanceCounter counter : PerformanceCounter.values()) {
            counterSnapshots.put(counter, COUNTERS[counter.ordinal()]);
        }
        return new PerformanceSnapshot(enabled, timingSnapshots, counterSnapshots);
    }

    public static boolean isEnabled() {
        return enabled;
    }

    /** Returns zero while profiling is disabled, avoiding timer reads and wrapper allocations. */
    public static long startTiming() {
        return enabled ? System.nanoTime() : 0L;
    }

    public static void finishTiming(PerformanceTiming timing, long startedAt) {
        if (startedAt == 0L) return;
        record(timing, Math.max(0L, System.nanoTime() - startedAt));
    }

    public static void enable() {
        reset();
        enabled = true;
    }

    public static void disable() {
        enabled = false;
    }

    public static void reset() {
        for (RollingTimingWindow timing : TIMINGS) timing.clear();
        Arrays.fill(COUNTERS, 0L);
    }
}
