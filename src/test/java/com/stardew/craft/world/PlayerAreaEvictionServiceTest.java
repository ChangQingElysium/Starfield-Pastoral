package com.stardew.craft.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerAreaEvictionServiceTest {
    @Test
    void activeStoryGateAlsoEvictsCreativeAndSpectatorPlayers() {
        assertTrue(PlayerAreaEvictionService.isEvictionRequired(true, true, false));
        assertTrue(PlayerAreaEvictionService.isEvictionRequired(true, false, true));
        assertTrue(PlayerAreaEvictionService.isEvictionRequired(true, false, false));
        assertFalse(PlayerAreaEvictionService.isEvictionRequired(false, true, true));
    }
}
