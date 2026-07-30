package com.stardew.craft.combat.skill;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SilverSaberFoldbackStateTest {
    @Test
    void foldbackWindowIsInclusiveAndCannotCrossDimensions() {
        assertTrue(SilverSaberFoldbackState.shouldRemainActive(
                120L,
                120L,
                true
        ));
        assertFalse(SilverSaberFoldbackState.shouldRemainActive(
                120L,
                121L,
                true
        ));
        assertFalse(SilverSaberFoldbackState.shouldRemainActive(
                120L,
                100L,
                false
        ));
    }
}
