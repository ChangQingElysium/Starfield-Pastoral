package com.stardew.craft.farm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FarmOccupancyTrackerTest {
    @Test
    void duplicateEntriesAndFarmSwitchesKeepCountsBalanced() {
        FarmOccupancyTracker<String> tracker = new FarmOccupancyTracker<>();

        assertTrue(tracker.enter("a", 1).changed());
        assertFalse(tracker.enter("a", 1).changed());
        tracker.enter("b", 1);
        assertEquals(2, tracker.count(1));

        FarmOccupancyTracker.Transition switched = tracker.enter("a", 2);
        assertEquals(1, switched.previous().orElseThrow().count());
        assertEquals(1, tracker.count(1));
        assertEquals(1, tracker.count(2));

        tracker.leave("a");
        assertFalse(tracker.isOccupied(2));
        assertTrue(tracker.leave("a").isEmpty());
    }

    @Test
    void clearRemovesAllOccupancy() {
        FarmOccupancyTracker<String> tracker = new FarmOccupancyTracker<>();
        tracker.enter("a", 1);
        tracker.enter("b", 2);

        tracker.clear();

        assertEquals(0, tracker.count(1));
        assertEquals(0, tracker.count(2));
    }
}
