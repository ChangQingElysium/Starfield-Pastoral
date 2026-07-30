package com.stardew.craft.player;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerStardewDataDirtyStateTest {
    @Test
    void assigningTheCurrentMoneyDoesNotRestartTheSyncLoop() {
        PlayerStardewData data = new PlayerStardewData(UUID.randomUUID());

        data.setMoney(500);
        assertFalse(data.isDirty());

        data.setMoney(250);
        assertTrue(data.isDirty());

        data.markClean();
        data.setMoney(250);
        assertFalse(data.isDirty());
    }
}
