package com.stardew.craft.player;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FishSmokerRecipeUnlockMigrationTest {
    @Test
    void removesTheFormerErroneousDefaultGrantExactlyOnce() {
        UUID playerId = UUID.randomUUID();
        PlayerStardewData data = new PlayerStardewData(playerId);
        assertTrue(data.unlockRecipe("fish_smoker"));

        assertTrue(data.applyRecipeUnlockMigrations());
        assertFalse(data.isRecipeUnlocked("fish_smoker"));
        assertFalse(data.applyRecipeUnlockMigrations());

        PlayerStardewData reloaded = PlayerStardewData.fromNBT(data.toNBT(), playerId);
        assertFalse(reloaded.applyRecipeUnlockMigrations());
        assertFalse(reloaded.isRecipeUnlocked("fish_smoker"));
    }

    @Test
    void purchaseAfterMigrationSurvivesReconnect() {
        UUID playerId = UUID.randomUUID();
        PlayerStardewData data = new PlayerStardewData(playerId);
        assertTrue(data.applyRecipeUnlockMigrations());
        assertTrue(data.unlockRecipe("fish_smoker"));

        CompoundTag saved = data.toNBT();
        PlayerStardewData reloaded = PlayerStardewData.fromNBT(saved, playerId);

        assertFalse(reloaded.applyRecipeUnlockMigrations());
        assertTrue(reloaded.isRecipeUnlocked("fish_smoker"));
    }
}
