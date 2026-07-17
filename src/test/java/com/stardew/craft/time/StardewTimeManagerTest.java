package com.stardew.craft.time;

import org.junit.jupiter.api.Test;
import net.minecraft.nbt.CompoundTag;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StardewTimeManagerTest {

    @Test
    void halfSpeedAdvancesExactlyOnceEveryTwoTicks() {
        StardewTimeManager.TimeAdvance first = StardewTimeManager.calculateTimeAdvance(0.0D, 0.5D);
        StardewTimeManager.TimeAdvance second = StardewTimeManager.calculateTimeAdvance(first.remainder(), 0.5D);

        assertEquals(0L, first.wholeTicks());
        assertEquals(0.5D, first.remainder(), 1.0E-9D);
        assertEquals(1L, second.wholeTicks());
        assertEquals(0.0D, second.remainder(), 1.0E-9D);
    }

    @Test
    void arbitraryFractionalSpeedDoesNotRoundToWholeMultipliers() {
        double remainder = 0.0D;
        long ticks = 0L;
        for (int i = 0; i < 10; i++) {
            StardewTimeManager.TimeAdvance advance =
                StardewTimeManager.calculateTimeAdvance(remainder, 1.4D);
            ticks += advance.wholeTicks();
            remainder = advance.remainder();
        }

        assertEquals(14L, ticks);
        assertEquals(0.0D, remainder, 1.0E-8D);
    }

    @Test
    void highSpeedRetainsFractionalPartAcrossTicks() {
        StardewTimeManager.TimeAdvance first = StardewTimeManager.calculateTimeAdvance(0.0D, 2.75D);
        StardewTimeManager.TimeAdvance second = StardewTimeManager.calculateTimeAdvance(first.remainder(), 2.75D);

        assertEquals(2L, first.wholeTicks());
        assertEquals(0.75D, first.remainder(), 1.0E-9D);
        assertEquals(3L, second.wholeTicks());
        assertEquals(0.5D, second.remainder(), 1.0E-9D);
    }

    @Test
    void legacyOffsetSaveMigratesFromCalendarAndClockFields() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("currentTime", 720);
        tag.putInt("currentDay", 3);
        tag.putInt("currentSeason", 1);
        tag.putInt("currentYear", 2);
        tag.putLong("dayTimeOffset", 123456L);

        StardewTimeManager loaded = StardewTimeManager.load(tag, null);
        long completedDays = 112L + 28L + 2L;

        assertEquals(
            completedDays * 24000L
                + com.stardew.craft.event.DimensionEventHandler.stardewMinutesToMcTime(720),
            loaded.getIndependentDayTime()
        );
    }
}
