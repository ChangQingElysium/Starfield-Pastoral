package com.stardew.craft.casino;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stardew.craft.client.gui.StardewRealtimeScreen;
import com.stardew.craft.client.gui.casino.CalicoJackScreen;
import com.stardew.craft.client.gui.casino.SlotsScreen;
import com.stardew.craft.dimension.StardewValleyPrebuiltRegionInstaller;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CasinoLocationResourceTest {
    @Test
    void casinoRegionMatchesApprovedMapBoundsAndClubMusic() throws Exception {
        JsonObject root;
        try (var stream = getClass().getClassLoader().getResourceAsStream(
                "data/stardewcraft/locations/fixed_interiors.json")) {
            assertNotNull(stream);
            root = JsonParser.parseReader(new InputStreamReader(
                    stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }

        JsonObject casino = null;
        for (var element : root.getAsJsonArray("regions")) {
            JsonObject region = element.getAsJsonObject();
            if ("casino".equals(region.get("id").getAsString())) {
                casino = region;
                break;
            }
        }
        assertNotNull(casino);
        assertArrayEquals(
                new int[] {-250, 28, -171},
                coordinates(casino.getAsJsonArray("min")));
        assertArrayEquals(
                new int[] {-219, 40, -153},
                coordinates(casino.getAsJsonArray("max")));
        assertEquals(
                "stardewcraft:music_clubloop",
                casino.getAsJsonObject("properties")
                        .get("stardewcraft:music_profile")
                        .getAsString());
    }

    @Test
    void casinoMapRevisionAndMinigamePauseContractsAreEnabled() {
        assertEquals(12, StardewValleyPrebuiltRegionInstaller.CURRENT_PREGEN_VERSION);
        assertFalse(StardewRealtimeScreen.class.isAssignableFrom(CalicoJackScreen.class));
        assertFalse(StardewRealtimeScreen.class.isAssignableFrom(SlotsScreen.class));
    }

    @Test
    void originalClubBoopCueIsPackaged() {
        assertNotNull(getClass().getClassLoader().getResource(
                "assets/stardewcraft/sounds/boop.ogg"));
    }

    private static int[] coordinates(JsonArray values) {
        return new int[] {
                values.get(0).getAsInt(),
                values.get(1).getAsInt(),
                values.get(2).getAsInt()
        };
    }
}
