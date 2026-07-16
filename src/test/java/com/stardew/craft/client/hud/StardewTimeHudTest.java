package com.stardew.craft.client.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StardewTimeHudTest {

    @Test
    void runningTimeAlwaysUsesItsFullColor() {
        assertEquals(0xFF000000, StardewTimeHud.pausedTimeTextColor(0xFF000000, false, false, 0L));
    }

    @Test
    void frozenTimeAlternatesBetweenHalfAndFullIntensityEachSecond() {
        assertEquals(0x80000000, StardewTimeHud.pausedTimeTextColor(0xFF000000, true, false, 500L));
        assertEquals(0xFF000000, StardewTimeHud.pausedTimeTextColor(0xFF000000, true, false, 1500L));
        assertEquals(0x807F0000, StardewTimeHud.pausedTimeTextColor(0xFFFF0000, true, false, 500L));
    }

    @Test
    void blackFadeSuppressesTheFrozenTimeBlink() {
        assertEquals(0xFF000000, StardewTimeHud.pausedTimeTextColor(0xFF000000, true, true, 500L));
    }
}
