package com.stardew.craft.server.performance;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class PerformanceReportFormatter {
    private PerformanceReportFormatter() {}

    public static List<String> format(PerformanceSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        List<String> lines = new ArrayList<>();
        lines.add("Stardew server performance (profiling "
                + (snapshot.enabled() ? "enabled" : "disabled") + ")");
        for (PerformanceTiming timing : PerformanceTiming.values()) {
            TimingSummary summary = snapshot.timings().getOrDefault(timing, TimingSummary.ZERO);
            lines.add(String.format(Locale.ROOT,
                    "%s samples=%d avg=%.3fms p95=%.3fms p99=%.3fms max=%.3fms",
                    timing, summary.sampleCount(), summary.averageMillis(), summary.p95Millis(),
                    summary.p99Millis(), summary.maxMillis()));
        }
        for (PerformanceCounter counter : PerformanceCounter.values()) {
            lines.add(counter + "=" + snapshot.counters().getOrDefault(counter, 0L));
        }
        return List.copyOf(lines);
    }
}
