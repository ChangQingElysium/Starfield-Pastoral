package com.stardew.craft.combat.skill;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InsectDashChainStateTest {
    @Test
    void chainWindowIsInclusiveAndCannotCrossDimensions() {
        assertTrue(InsectDashChainState.shouldRemainActive(140L, 140L, true));
        assertFalse(InsectDashChainState.shouldRemainActive(140L, 141L, true));
        assertFalse(InsectDashChainState.shouldRemainActive(140L, 120L, false));
    }

    @Test
    void stageProgressionStopsAtTheAuthoredThirdDash() {
        assertEquals(1, InsectDashChainState.nextStageFor(0));
        assertEquals(2, InsectDashChainState.nextStageFor(1));
        assertEquals(3, InsectDashChainState.nextStageFor(2));
        assertEquals(3, InsectDashChainState.nextStageFor(3));
        assertEquals(40, InsectDashChainState.CHAIN_WINDOW_TICKS);
    }
}
