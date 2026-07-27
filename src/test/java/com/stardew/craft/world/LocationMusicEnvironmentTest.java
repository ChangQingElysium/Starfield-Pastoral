package com.stardew.craft.world;

import com.stardew.craft.api.v1.world.StardewLocation;
import com.stardew.craft.api.v1.world.StardewLocationDefinition;
import com.stardew.craft.api.v1.world.StardewLocationEnvironmentKeys;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocationMusicEnvironmentTest {
    @Test
    void trackWindowFallsBackOutsideHours() {
        StardewLocation location = location(Map.of(
                StardewLocationEnvironmentKeys.MUSIC_PROFILE,
                "minecraft:entity.player.levelup",
                StardewLocationEnvironmentKeys.MUSIC_START_TIME,
                "0900",
                StardewLocationEnvironmentKeys.MUSIC_END_TIME,
                "1700"));

        assertEquals(LocationMusicEnvironment.Decision.TRACK,
                LocationMusicEnvironment.resolve(
                        location, 9 * 60).decision());
        assertEquals(LocationMusicEnvironment.Decision.INHERIT,
                LocationMusicEnvironment.resolve(
                        location, 17 * 60).decision());
    }

    @Test
    void silenceIsExplicitAndOvernightWindowsWrap() {
        StardewLocation location = location(Map.of(
                StardewLocationEnvironmentKeys.MUSIC_PROFILE,
                StardewLocationEnvironmentKeys.MUSIC_SILENT,
                StardewLocationEnvironmentKeys.MUSIC_START_TIME,
                "2200",
                StardewLocationEnvironmentKeys.MUSIC_END_TIME,
                "0200"));

        assertEquals(LocationMusicEnvironment.Decision.SILENCE,
                LocationMusicEnvironment.resolve(
                        location, 23 * 60).decision());
        assertEquals(LocationMusicEnvironment.Decision.SILENCE,
                LocationMusicEnvironment.resolve(
                        location, 25 * 60).decision());
        assertEquals(LocationMusicEnvironment.Decision.INHERIT,
                LocationMusicEnvironment.resolve(
                        location, 12 * 60).decision());
    }

    @Test
    void childMayOverrideTimeWhileInheritingTheProfile() {
        StardewLocationDefinition child = definition(Map.of(
                StardewLocationEnvironmentKeys.MUSIC_START_TIME,
                "0900"));
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                "music_test", "child");

        assertTrue(LocationMusicEnvironment.validate(
                id, child, true).isEmpty());
        assertTrue(LocationMusicEnvironment.validate(
                id, child, false).isPresent());
    }

    private static StardewLocation location(
            Map<ResourceLocation, String> properties
    ) {
        return new StardewLocation(
                ResourceLocation.fromNamespaceAndPath(
                        "music_test", "orchard"),
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft", "stardew_valley"),
                BlockPos.ZERO,
                new BlockPos(10, 10, 10),
                "",
                List.of(),
                0,
                false,
                null,
                Component.literal("Orchard"),
                Component.empty(),
                null,
                Set.of(),
                properties);
    }

    private static StardewLocationDefinition definition(
            Map<ResourceLocation, String> properties
    ) {
        return new StardewLocationDefinition(
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft", "stardew_valley"),
                "",
                new StardewLocationDefinition.Vec3i(0, 0, 0),
                new StardewLocationDefinition.Vec3i(10, 10, 10),
                List.of(),
                0,
                false,
                null,
                Component.empty(),
                Component.empty(),
                null,
                List.of(),
                properties);
    }
}
