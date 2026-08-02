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

    @Test
    void healthAndEnergyUseVitalsNetworkDirtyWithoutLosingPersistenceDirty() {
        PlayerStardewData data = new PlayerStardewData(UUID.randomUUID());

        data.setHealth(80);
        data.setEnergy(200.0F);

        assertTrue(data.isDirty());
        assertTrue(data.isVitalsSyncDirty());
        assertFalse(data.isFullSyncDirty());

        data.markVitalsSyncClean();
        assertFalse(data.isVitalsSyncDirty());
        assertTrue(data.isDirty());

        data.markClean();
        assertFalse(data.isDirty());
    }

    @Test
    void fullSyncConsumesVitalsNetworkDirtyButNotPersistenceDirty() {
        PlayerStardewData data = new PlayerStardewData(UUID.randomUUID());

        data.setHealth(75);
        data.setMoney(250);

        assertTrue(data.isVitalsSyncDirty());
        assertTrue(data.isFullSyncDirty());

        data.markFullSyncClean();
        assertFalse(data.isVitalsSyncDirty());
        assertFalse(data.isFullSyncDirty());
        assertTrue(data.isDirty());
    }
}
