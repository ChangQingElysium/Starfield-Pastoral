package com.stardew.craft.platform.fabric.event.sleep;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class SleepValidationTest {
    @Test
    void nestedAttemptsRestoreTheOuterPhaseAndNeverBorrowAnotherPlayersProbe() {
        Object first = new Object(), second = new Object();
        try (var outer = SleepValidation.open(first)) {
            assertTrue(SleepValidation.probing(first)); assertFalse(SleepValidation.probing(second));
            try (var inner = SleepValidation.open(second)) {
                assertFalse(SleepValidation.probing(first)); assertTrue(SleepValidation.probing(second));
                assertThrows(IllegalStateException.class, outer::finishValidation);
                inner.finishValidation(); assertFalse(SleepValidation.probing(second));
            }
            assertTrue(SleepValidation.probing(first)); outer.finishValidation(); assertFalse(SleepValidation.probing(first));
            try (var again = SleepValidation.open(first)) { assertTrue(SleepValidation.probing(first)); }
            assertFalse(SleepValidation.probing(first));
        }
        assertFalse(SleepValidation.probing(first)); assertFalse(SleepValidation.probing(second));
    }
    @Test
    void validationAndListenerFailuresAlwaysUnwindAndAnotherThreadCannotSeeTheScope() throws Exception {
        Object player = new Object();
        assertThrows(IllegalArgumentException.class, () -> { try (var frame = SleepValidation.open(player)) { throw new IllegalArgumentException("validation failed"); } });
        assertFalse(SleepValidation.probing(player));
        try (var frame = SleepValidation.open(player)) {
            var result = new java.util.concurrent.atomic.AtomicBoolean(true);
            var thread = new Thread(() -> result.set(SleepValidation.probing(player))); thread.start(); thread.join(); assertFalse(result.get());
            frame.finishValidation();
            assertThrows(IllegalArgumentException.class, () -> { try (var nested = SleepValidation.open(player)) { nested.finishValidation(); throw new IllegalArgumentException("listener failed"); } });
            assertFalse(SleepValidation.probing(player));
        }
        assertFalse(SleepValidation.probing(player));
    }
}
