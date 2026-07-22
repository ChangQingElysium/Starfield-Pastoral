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

class HenchmanResourceTest {
    private static final Path PROJECT = Path.of(System.getProperty("stardewcraft.projectDir"));
    private static final List<String> LANGUAGES = List.of(
            "de_de", "en_us", "es_es", "fr_fr", "hu_hu", "it_it",
            "ja_jp", "ko_kr", "pt_br", "ru_ru", "tr_tr", "zh_cn");

    @Test
    void usesConfirmedGuardPositionAndLockedArea() throws Exception {
        assertEquals(39.0D, HenchmanService.LOCKED_AREA.minX);
        assertEquals(44.0D, HenchmanService.LOCKED_AREA.minY);
        assertEquals(-257.0D, HenchmanService.LOCKED_AREA.minZ);
        assertEquals(68.0D, HenchmanService.LOCKED_AREA.maxX);
        assertEquals(59.0D, HenchmanService.LOCKED_AREA.maxY);
        assertEquals(-234.0D, HenchmanService.LOCKED_AREA.maxZ);

        JsonObject points = json("src/main/resources/data/stardewcraft/npc/events/npc_route_points.json")
                .getAsJsonObject("points");
        JsonObject guard = points.getAsJsonObject("henchman_guard");
        assertEquals(51, guard.get("x").getAsInt());
        assertEquals(48, guard.get("y").getAsInt());
        assertEquals(-235, guard.get("z").getAsInt());
        assertFalse(guard.get("indoor").getAsBoolean());

        JsonObject schedule = json("src/main/resources/data/stardewcraft/npc/schedules/henchman.json");
        assertEquals("witchswamp @henchman_guard 2",
                schedule.getAsJsonObject("default").get("600").getAsString());
    }

    @Test
    void registersModelAndFixedNpcProfile() throws Exception {
        for (String path : List.of(
                "src/main/resources/assets/stardewcraft/geo/entity/npc/henchman.geo.json",
                "src/main/resources/assets/stardewcraft/animations/entity/npc/henchman.animation.json",
                "src/main/resources/assets/stardewcraft/textures/entity/npc/henchman.png",
                "src/main/resources/assets/stardewcraft/textures/portraits/henchman.png")) {
            assertTrue(Files.isRegularFile(PROJECT.resolve(path)), path);
            assertTrue(Files.size(PROJECT.resolve(path)) > 0L, path);
        }

        JsonObject profiles = json("src/main/resources/data/stardewcraft/npc/capabilities/base_profiles.json");
        boolean found = profiles.getAsJsonArray("npcs").asList().stream()
                .map(element -> element.getAsJsonObject())
                .anyMatch(profile -> "henchman".equals(profile.get("id").getAsString())
                        && profile.get("implemented").getAsBoolean()
                        && !profile.get("pathing_enabled").getAsBoolean());
        assertTrue(found);
    }

    @Test
    void questAndAllOriginalDialogueBranchesAreBundled() throws Exception {
        JsonObject quests = json("src/main/resources/data/stardewcraft/quests.json");
        assertTrue(quests.has("27"));
        assertTrue(quests.get("27").getAsString().startsWith(
                "Basic/stardewcraft.quest.27.title/stardewcraft.quest.27.description/"));

        for (String language : LANGUAGES) {
            JsonObject translations = json("src/main/resources/assets/stardewcraft/lang/" + language + ".json");
            assertTrue(translations.has("entity.stardewcraft.npc.henchman"), language);
            for (int line = 1; line <= 5; line++) {
                assertTrue(translations.has("stardewcraft.npc.henchman." + line), language + " line " + line);
            }
            assertTrue(translations.has("stardewcraft.quest.27.title"), language);
            assertTrue(translations.has("stardewcraft.quest.27.description"), language);
            assertTrue(translations.has("stardewcraft.quest.27.objective"), language);
        }
    }

    @Test
    void movingAsideWaitsForTheDialogueClosePacket() throws Exception {
        String service = Files.readString(PROJECT.resolve(
                "src/main/java/com/stardew/craft/world/HenchmanService.java"));
        String closePayload = Files.readString(PROJECT.resolve(
                "src/main/java/com/stardew/craft/network/payload/CloseNpcDialoguePayload.java"));

        assertTrue(service.contains("data.addMailFlag(MOVE_PENDING_FLAG)"));
        assertTrue(service.contains("public static void onDialogueClosed"));
        assertTrue(service.contains("data.addMailFlag(GONE_FLAG)"));
        assertTrue(closePayload.contains("HenchmanService.onDialogueClosed"));
    }

    private static JsonObject json(String relativePath) throws Exception {
        return JsonParser.parseString(Files.readString(PROJECT.resolve(relativePath))).getAsJsonObject();
    }
}
