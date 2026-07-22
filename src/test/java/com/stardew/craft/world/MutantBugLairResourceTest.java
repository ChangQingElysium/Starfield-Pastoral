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

class MutantBugLairResourceTest {
    private static final List<String> LANGUAGES = List.of(
            "de_de", "en_us", "es_es", "fr_fr", "hu_hu", "it_it",
            "ja_jp", "ko_kr", "pt_br", "ru_ru", "tr_tr", "zh_cn");

    @Test
    void usesConfirmedDomainAndFloorCoordinates() {
        assertTrue(MutantBugLairArea.contains(-19, -15, -46));
        assertTrue(MutantBugLairArea.contains(53, 6, 48));
        assertFalse(MutantBugLairArea.contains(-20, -15, -46));
        assertFalse(MutantBugLairArea.contains(53, 7, 48));

        assertEquals(-7, MutantBugLairArea.SPAWN_MIN_X);
        assertEquals(44, MutantBugLairArea.SPAWN_MAX_X);
        assertEquals(-11, MutantBugLairArea.SPAWN_FLOOR_Y);
        assertEquals(-10, MutantBugLairArea.SPAWN_Y);
        assertEquals(-21, MutantBugLairArea.SPAWN_MIN_Z);
        assertEquals(27, MutantBugLairArea.SPAWN_MAX_Z);
    }

    @Test
    void registersOriginalBugLevelLoopAsset() throws Exception {
        JsonObject sounds = json("src/main/resources/assets/stardewcraft/sounds.json");
        String asset = sounds.getAsJsonObject("music_bug_level_loop")
                .getAsJsonArray("sounds").get(0).getAsJsonObject()
                .get("name").getAsString();
        assertEquals("stardewcraft:music/bug_level_loop", asset);

        Path audio = projectRoot().resolve(
                "src/main/resources/assets/stardewcraft/sounds/music/bug_level_loop.ogg");
        assertTrue(Files.isRegularFile(audio));
        assertTrue(Files.size(audio) > 0);
    }

    @Test
    void krobusUnlockUsesTheTwoVanillaDebuffSpellBolts() throws Exception {
        String vanillaNpc = Files.readString(projectRoot().resolve("源文件/StardewValley/NPC.cs"));
        int effectStart = vanillaNpc.indexOf("who.mailReceived.Add(\"krobusUnseal\")");
        String unlockSection = vanillaNpc.substring(effectStart, vanillaNpc.indexOf("return true;", effectStart));
        assertEquals(2, unlockSection.split("startSound = \"debuffSpell\"", -1).length - 1);

        JsonObject sounds = json("src/main/resources/assets/stardewcraft/sounds.json");
        String spellAsset = sounds.getAsJsonObject("debuff_spell")
                .getAsJsonArray("sounds").get(0).getAsJsonObject()
                .get("name").getAsString();
        assertEquals("stardewcraft:debuffspell", spellAsset);
        assertTrue(Files.isRegularFile(projectRoot().resolve(
                "src/main/resources/assets/stardewcraft/sounds/debuffspell.ogg")));
    }

    @Test
    void lairMarkerIsAppliedBeforePublicAreaJoinFilterRuns() throws Exception {
        String spawnHandler = Files.readString(projectRoot().resolve(
                "src/main/java/com/stardew/craft/event/MineMonsterSpawnHandler.java"));
        int overload = spawnHandler.indexOf("Consumer<Mob> configureBeforeSpawn");
        int configure = spawnHandler.indexOf("configureBeforeSpawn.accept(mob)", overload);
        int joinWorld = spawnHandler.indexOf("level.addFreshEntity(mob)", overload);
        assertTrue(overload >= 0 && configure > overload && joinWorld > configure,
                "caller marker must exist before EntityJoinLevelEvent is fired");

        String publicProtection = Files.readString(projectRoot().resolve(
                "src/main/java/com/stardew/craft/event/FarmAreaProtectionEvents.java"));
        assertTrue(publicProtection.contains("MutantBugLairService.isLairMonster(mob)"));
    }

    @Test
    void hasDistinctSubduedGreenWaterAndVanillaBuglandFish() throws Exception {
        JsonObject biome = json("src/main/resources/data/stardewcraft/worldgen/biome/mutant_bug_lair.json");
        int water = biome.getAsJsonObject("effects").get("water_color").getAsInt();
        JsonObject sewer = json("src/main/resources/data/stardewcraft/worldgen/biome/sewers.json");
        int sewerWater = sewer.getAsJsonObject("effects").get("water_color").getAsInt();
        assertEquals(0x479657, water);
        assertFalse(water == sewerWater);

        String fishing = Files.readString(projectRoot().resolve(
                "src/main/resources/data/stardewcraft/fishing/locations/bugland.json"));
        assertTrue(fishing.contains("#stardewcraft:is_mutant_bug_lair"));
        assertTrue(fishing.contains("\"id\": \"slimejack\""));
        assertTrue(fishing.contains("\"id\": \"carp\""));
        assertTrue(fishing.contains("\"id\": \"green_algae\""));
    }

    @Test
    void everyBundledLanguageNamesTheLairAndItsInteractions() throws Exception {
        for (String language : LANGUAGES) {
            JsonObject translations = json("src/main/resources/assets/stardewcraft/lang/" + language + ".json");
            assertTrue(translations.has("stardewcraft.location.mutant_bug_lair"));
            assertTrue(translations.has("stardewcraft.mutant_bug_lair.seal_locked"));
            assertTrue(translations.has("stardewcraft.mutant_bug_lair.dark_talisman_received"));
        }
    }

    private static JsonObject json(String path) throws Exception {
        return JsonParser.parseString(Files.readString(projectRoot().resolve(path))).getAsJsonObject();
    }

    private static Path projectRoot() {
        return Path.of(System.getProperty("stardewcraft.projectDir"));
    }
}
