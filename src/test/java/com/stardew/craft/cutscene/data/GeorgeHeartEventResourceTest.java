package com.stardew.craft.cutscene.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeorgeHeartEventResourceTest {
    private static final List<String> LANGUAGES = List.of(
            "de_de", "en_us", "es_es", "fr_fr", "hu_hu", "it_it",
            "ja_jp", "ko_kr", "pt_br", "ru_ru", "tr_tr", "zh_cn");

    @Test
    void sixHeartEventUsesVanillaGateAndCapturedMinecraftScene() {
        JsonObject event = json("data/stardewcraft/cutscene_events/george_6heart.json");
        assertEquals("george_6heart", event.get("id").getAsString());
        assertTrue(event.getAsJsonArray("legacy_ids").asList().stream()
                .map(JsonElement::getAsString).anyMatch("18"::equals));

        JsonObject trigger = event.getAsJsonObject("trigger");
        assertEquals("joshhouse", trigger.get("location").getAsString());
        assertArrayEquals(new int[]{37, 22, -19}, ints(trigger.getAsJsonArray("area_min")));
        assertArrayEquals(new int[]{64, 26, 5}, ints(trigger.getAsJsonArray("area_max")));
        assertPrecondition(event, "friendship", "min", 1500);
        assertPrecondition(event, "npc_present", "npc", "george");

        JsonArray commands = event.getAsJsonArray("commands");
        assertTrue(hasPosition(commands, "camera", 58.283, 22.171, 1.485));
        assertTrue(hasPosition(commands, "camera", 60.029, 21.995, -0.692));
        assertTrue(hasPosition(commands, "spawn_actor", 51.5, 22.0, -0.5));
        assertTrue(hasPosition(commands, "navigate_actor", 58.5, 22.0, -1.5));
        assertTrue(hasPosition(commands, "spawn_actor", 59.5, 22.0, -2.5));
        assertTrue(hasPosition(commands, "navigate_actor", 59.5, 22.0, -1.5));
        assertTrue(hasCommand(commands, "music", "track", "MUSIC_SADPIANO"));
        assertTrue(hasCommand(commands, "play_sound", "sound", "stardewcraft:ship"));
        assertTrue(hasCommand(commands, "play_sound", "sound", "stardewcraft:coin"));
    }

    @Test
    void allSupportedLanguagesContainTheSevenVanillaLines() {
        List<String> suffixes = List.of(
                "struggle", "thanks", "accident", "hospital",
                "spry", "silence", "kindness");
        for (String language : LANGUAGES) {
            JsonObject lang = json("assets/stardewcraft/lang/" + language + ".json");
            for (String suffix : suffixes) {
                String key = "event.george.18." + suffix;
                assertTrue(lang.has(key), language + " is missing " + key);
                assertFalse(lang.get(key).getAsString().isBlank(), language + " has blank " + key);
            }
        }
    }

    @Test
    void sadPianoUsesTheImportedVanillaCue() throws Exception {
        JsonObject sounds = json("assets/stardewcraft/sounds.json");
        assertEquals("stardewcraft:music/sadpiano",
                sounds.getAsJsonObject("music_sadpiano")
                        .getAsJsonArray("sounds").get(0).getAsJsonObject()
                        .get("name").getAsString());
        try (InputStream stream = resource("assets/stardewcraft/sounds/music/sadpiano.ogg")) {
            assertEquals(4, stream.readNBytes(4).length);
        }
    }

    private static void assertPrecondition(
            JsonObject event, String type, String field, int value) {
        assertTrue(event.getAsJsonArray("preconditions").asList().stream()
                .map(JsonElement::getAsJsonObject)
                .anyMatch(entry -> type.equals(entry.get("type").getAsString())
                        && entry.has(field) && entry.get(field).getAsInt() == value));
    }

    private static void assertPrecondition(
            JsonObject event, String type, String field, String value) {
        assertTrue(event.getAsJsonArray("preconditions").asList().stream()
                .map(JsonElement::getAsJsonObject)
                .anyMatch(entry -> type.equals(entry.get("type").getAsString())
                        && entry.has(field) && value.equals(entry.get(field).getAsString())));
    }

    private static int[] ints(JsonArray values) {
        int[] result = new int[values.size()];
        for (int index = 0; index < values.size(); index++) {
            result[index] = values.get(index).getAsInt();
        }
        return result;
    }

    private static boolean hasPosition(
            JsonArray commands, String command, double x, double y, double z) {
        return commands.asList().stream()
                .map(JsonElement::getAsJsonObject)
                .anyMatch(entry -> command.equals(entry.get("cmd").getAsString())
                        && entry.has("x") && Double.compare(entry.get("x").getAsDouble(), x) == 0
                        && entry.has("y") && Double.compare(entry.get("y").getAsDouble(), y) == 0
                        && entry.has("z") && Double.compare(entry.get("z").getAsDouble(), z) == 0);
    }

    private static boolean hasCommand(
            JsonArray commands, String command, String field, String value) {
        return commands.asList().stream()
                .map(JsonElement::getAsJsonObject)
                .anyMatch(entry -> command.equals(entry.get("cmd").getAsString())
                        && entry.has(field) && value.equals(entry.get(field).getAsString()));
    }

    private static JsonObject json(String path) {
        try (InputStream stream = resource(path)) {
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (Exception exception) {
            throw new AssertionError(path, exception);
        }
    }

    private static InputStream resource(String path) {
        InputStream stream = GeorgeHeartEventResourceTest.class.getClassLoader()
                .getResourceAsStream(path);
        assertNotNull(stream, "missing resource " + path);
        return stream;
    }
}
