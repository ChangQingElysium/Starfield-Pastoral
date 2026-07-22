package com.stardew.craft.fishing.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FishingDataManagerCatchTypeTest {
    @Test
    void vanillaAlgaeAndSeaweedBypassTheBobberBar() {
        assertTrue(FishingDataManager.isNonFishCatchable("stardewcraft:seaweed"));
        assertTrue(FishingDataManager.isNonFishCatchable("stardewcraft:green_algae"));
        assertTrue(FishingDataManager.isNonFishCatchable("stardewcraft:white_algae"));
    }

    @Test
    void actualFishStillUseTheBobberBar() {
        assertFalse(FishingDataManager.isNonFishCatchable("stardewcraft:carp"));
    }
}
