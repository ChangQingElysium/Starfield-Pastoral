package com.stardew.craft.server.performance;

public record TimingSummary(
        long sampleCount,
        double averageMillis,
        double maxMillis,
        double p95Millis,
        double p99Millis
) {
    public static final TimingSummary ZERO = new TimingSummary(0L, 0.0D, 0.0D, 0.0D, 0.0D);
}
