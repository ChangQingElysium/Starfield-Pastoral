package com.stardew.craft.server.performance;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServerPerformanceRecorderTest {
    @AfterEach
    void reset() {
        ServerPerformanceRecorder.disable();
        ServerPerformanceRecorder.reset();
    }

    @Test
    void recordsTimingsAndSaturatesCounters() {
        ServerPerformanceRecorder.enable();
        ServerPerformanceRecorder.record(PerformanceTiming.SERVER_TICK, 2_000_000L);
        ServerPerformanceRecorder.increment(PerformanceCounter.CONTENT_SYNC_PACKETS, Long.MAX_VALUE);
        ServerPerformanceRecorder.increment(PerformanceCounter.CONTENT_SYNC_PACKETS, 1L);

        PerformanceSnapshot snapshot = ServerPerformanceRecorder.snapshot();
        assertEquals(1L, snapshot.timings().get(PerformanceTiming.SERVER_TICK).sampleCount());
        assertEquals(2.0D, snapshot.timings().get(PerformanceTiming.SERVER_TICK).averageMillis());
        assertEquals(Long.MAX_VALUE, snapshot.counters().get(PerformanceCounter.CONTENT_SYNC_PACKETS));
    }

    @Test
    void resetClearsEveryCategory() {
        ServerPerformanceRecorder.enable();
        ServerPerformanceRecorder.record(PerformanceTiming.OFFLINE_FARM_CATCH_UP, 1L);
        ServerPerformanceRecorder.increment(PerformanceCounter.FARM_CATCH_UP_OBJECTS, 2L);
        ServerPerformanceRecorder.reset();

        PerformanceSnapshot snapshot = ServerPerformanceRecorder.snapshot();
        assertEquals(0L, snapshot.timings().get(PerformanceTiming.OFFLINE_FARM_CATCH_UP).sampleCount());
        assertEquals(0L, snapshot.counters().get(PerformanceCounter.FARM_CATCH_UP_OBJECTS));
    }

    @Test
    void disabledProfilerDoesNotRecord() {
        ServerPerformanceRecorder.record(PerformanceTiming.SERVER_TICK, 2_000_000L);
        ServerPerformanceRecorder.increment(PerformanceCounter.CONTENT_SYNC_PACKETS, 1L);

        PerformanceSnapshot snapshot = ServerPerformanceRecorder.snapshot();
        assertEquals(false, snapshot.enabled());
        assertEquals(0L, snapshot.timings().get(PerformanceTiming.SERVER_TICK).sampleCount());
        assertEquals(0L, snapshot.counters().get(PerformanceCounter.CONTENT_SYNC_PACKETS));
    }
}
