package com.stardew.craft.event;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DimensionEventHandlerPassOutTest {
    @Test
    void multiplayerExhaustionBeforeTwoAmReturnsOnlyThatFarmerToBed() {
        assertTrue(DimensionEventHandler.shouldReturnEarlyPassOutToBed(1559, 2));
        assertFalse(DimensionEventHandler.shouldReturnEarlyPassOutToBed(1560, 2));
    }

    @Test
    void singlePlayerExhaustionUsesSharedDayAdvancePath() {
        assertFalse(DimensionEventHandler.shouldReturnEarlyPassOutToBed(900, 1));
        assertFalse(DimensionEventHandler.shouldReturnEarlyPassOutToBed(900, 0));
    }
}
