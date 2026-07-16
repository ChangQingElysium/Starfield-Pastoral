package com.stardew.craft.player;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerDisplayNameTest {
    @Test
    void preferredCharacterNameWinsOverMinecraftName() {
        PlayerStardewData data = new PlayerStardewData(UUID.randomUUID());
        data.setProfile("  Leah  ", "Stardrops", 1);

        assertEquals("Leah", PlayerDisplayName.resolve(data, "MinecraftAccount"));
    }

    @Test
    void minecraftNameIsOnlyUsedForIncompleteLegacyProfiles() {
        PlayerStardewData data = new PlayerStardewData(UUID.randomUUID());

        assertEquals("MinecraftAccount", PlayerDisplayName.resolve(data, " MinecraftAccount "));
        assertEquals("Player", PlayerDisplayName.resolve(data, " "));
    }
}
