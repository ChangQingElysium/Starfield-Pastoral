package com.stardew.craft.server.performance;

import java.util.Arrays;

final class RollingTimingWindow {
    private static final double NANOS_PER_MILLISECOND = 1_000_000.0D;

    private final long[] samples;
    private int sampleCount;
    private int nextIndex;

    RollingTimingWindow(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
        samples = new long[capacity];
    }

    void record(long nanoseconds) {
        samples[nextIndex] = Math.max(0L, nanoseconds);
        nextIndex = (nextIndex + 1) % samples.length;
        if (sampleCount < samples.length) sampleCount++;
    }

    TimingSummary snapshot() {
        if (sampleCount == 0) return TimingSummary.ZERO;
        long[] sorted = Arrays.copyOf(samples, sampleCount);
        Arrays.sort(sorted);
        double total = 0.0D;
        for (long sample : sorted) total += sample;
        return new TimingSummary(
                sampleCount,
                total / sampleCount / NANOS_PER_MILLISECOND,
                sorted[sampleCount - 1] / NANOS_PER_MILLISECOND,
                percentileMillis(sorted, 0.95D),
                percentileMillis(sorted, 0.99D));
    }

    void clear() {
        Arrays.fill(samples, 0L);
        sampleCount = 0;
        nextIndex = 0;
    }

    private static double percentileMillis(long[] sorted, double percentile) {
        int index = (int) Math.ceil(percentile * sorted.length) - 1;
        return sorted[index] / NANOS_PER_MILLISECOND;
    }
}
