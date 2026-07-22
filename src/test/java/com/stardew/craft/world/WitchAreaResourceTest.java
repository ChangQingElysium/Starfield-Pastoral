package com.stardew.craft.world;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WitchAreaResourceTest {
    private static final Path PROJECT = Path.of(System.getProperty("stardewcraft.projectDir"));
    private static final List<String> LANGUAGES = List.of(
            "de_de", "en_us", "es_es", "fr_fr", "hu_hu", "it_it",
            "ja_jp", "ko_kr", "pt_br", "ru_ru", "tr_tr", "zh_cn");

    @Test
    void usesConfirmedSwampAndHutBounds() {
        assertTrue(WitchArea.isInSwamp(39, 46, -257));
        assertTrue(WitchArea.isInSwamp(66, 59, -215));
        assertFalse(WitchArea.isInSwamp(38, 46, -257));
        assertFalse(WitchArea.isInSwamp(66, 45, -215));

        assertTrue(WitchArea.isInHut(44, 39, -256));
        assertTrue(WitchArea.isInHut(58, 43, -242));
        assertFalse(WitchArea.isInHut(44, 44, -242));
        assertFalse(WitchArea.isInHut(59, 39, -256));
    }

    @Test
    void usesConfirmedPortalAndInkCoordinates() {
        assertEquals(51, WitchAreaService.HUT_ENTRANCE_PORTAL_BASE.getX());
        assertEquals(48, WitchAreaService.HUT_ENTRANCE_PORTAL_BASE.getY());
        assertEquals(-243, WitchAreaService.HUT_ENTRANCE_PORTAL_BASE.getZ());
        assertEquals(51, WitchAreaService.HUT_EXIT_PORTAL_BASE.getX());
        assertEquals(40, WitchAreaService.HUT_EXIT_PORTAL_BASE.getY());
        assertEquals(-242, WitchAreaService.HUT_EXIT_PORTAL_BASE.getZ());
        assertEquals(48, WitchAreaService.MAGIC_INK_TABLE_POS.getX());
        assertEquals(40, WitchAreaService.MAGIC_INK_TABLE_POS.getY());
        assertEquals(-245, WitchAreaService.MAGIC_INK_TABLE_POS.getZ());
    }

    @Test
    void registersOriginalLocationAmbientTracks() throws Exception {
        JsonObject sounds = json("src/main/resources/assets/stardewcraft/sounds.json");
        assertEquals("stardewcraft:music/lava_ambient", firstSound(sounds, "music_lava_ambient"));
        assertEquals("stardewcraft:music/upper_ambient", firstSound(sounds, "music_upper_ambient"));
        assertTrue(Files.size(PROJECT.resolve(
                "src/main/resources/assets/stardewcraft/sounds/music/lava_ambient.ogg")) > 0L);
        assertTrue(Files.size(PROJECT.resolve(
                "src/main/resources/assets/stardewcraft/sounds/music/upper_ambient.ogg")) > 0L);

        String vanillaLocations = Files.readString(PROJECT.resolve("源文件/Content/Data/Locations.json"));
        int swamp = vanillaLocations.indexOf("\"WitchSwamp\"");
        int hut = vanillaLocations.indexOf("\"WitchHut\"", swamp);
        assertTrue(vanillaLocations.substring(swamp, hut).contains("\"MusicDefault\": \"Lava_Ambient\""));
        assertTrue(vanillaLocations.substring(hut, vanillaLocations.indexOf("\"WitchWarpCave\"", hut))
                .contains("\"MusicDefault\": \"Upper_Ambient\""));
    }

    @Test
    void witchSwampFishingUsesItsBiomeTagAndVanillaCatchTable() throws Exception {
        String fishing = Files.readString(PROJECT.resolve(
                "src/main/resources/data/stardewcraft/fishing/locations/witchswamp.json"));
        assertTrue(fishing.contains("#stardewcraft:is_witch_swamp"));
        assertTrue(fishing.contains("\"id\": \"void_salmon\""));
        assertTrue(fishing.contains("\"id\": \"void_mayonnaise\""));
        assertTrue(fishing.contains("\"chance\": 0.05"));
        assertTrue(fishing.contains("\"id\": \"catfish\""));

        JsonObject effects = json("src/main/resources/data/stardewcraft/worldgen/biome/witch_swamp.json")
                .getAsJsonObject("effects");
        assertEquals(0x315A8E, effects.get("water_color").getAsInt());
        assertEquals(0x16173A, effects.get("water_fog_color").getAsInt());
    }

    @Test
    void everyBundledLanguageNamesBothLocationsAndInkReward() throws Exception {
        for (String language : LANGUAGES) {
            JsonObject translations = json("src/main/resources/assets/stardewcraft/lang/" + language + ".json");
            assertTrue(translations.has("stardewcraft.location.witch_swamp"), language);
            assertTrue(translations.has("stardewcraft.location.witch_hut"), language);
            assertTrue(translations.has("stardewcraft.witch_hut.magic_ink_received"), language);
        }
    }

    private static String firstSound(JsonObject sounds, String event) {
        return sounds.getAsJsonObject(event).getAsJsonArray("sounds")
                .get(0).getAsJsonObject().get("name").getAsString();
    }

    private static JsonObject json(String relativePath) throws Exception {
        return JsonParser.parseString(Files.readString(PROJECT.resolve(relativePath))).getAsJsonObject();
    }
}
