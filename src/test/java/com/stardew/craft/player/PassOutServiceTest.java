package com.stardew.craft.player;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PassOutServiceTest {

    @Test
    void ownBedWaivesOvernightMoneyLoss() {
        assertEquals(0, PassOutService.calculateOvernightMoneyLoss(50_000, true));
    }

    @Test
    void overnightMoneyLossUsesTenPercentWithCap() {
        assertEquals(50, PassOutService.calculateOvernightMoneyLoss(500, false));
        assertEquals(1_000, PassOutService.calculateOvernightMoneyLoss(50_000, false));
        assertEquals(0, PassOutService.calculateOvernightMoneyLoss(0, false));
    }
}
