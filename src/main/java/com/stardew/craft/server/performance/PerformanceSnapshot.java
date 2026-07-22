package com.stardew.craft.server.performance;

import java.util.Map;

public record PerformanceSnapshot(
        boolean enabled,
        Map<PerformanceTiming, TimingSummary> timings,
        Map<PerformanceCounter, Long> counters
) {
    public PerformanceSnapshot {
        timings = Map.copyOf(timings);
        counters = Map.copyOf(counters);
    }
}
