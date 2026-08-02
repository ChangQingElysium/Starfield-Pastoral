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

class LinusHeartEventResourceTest {
    private static final List<String> LANGUAGES = List.of(
            "de_de", "en_us", "es_es", "fr_fr", "hu_hu", "it_it",
            "ja_jp", "ko_kr", "pt_br", "ru_ru", "tr_tr", "zh_cn");

    @Test
    void eventsUseSemanticIdsPreserveVanillaIdsAndUserSuppliedTriggerAreas() {
        JsonObject early = event("linus_50point");
        JsonObject four = event("linus_4heart");
        JsonObject eight = event("linus_8heart");

        assertEquals("linus_50point", early.get("id").getAsString());
        assertLegacyId(early, "502969");
        assertPrecondition(early, "not_saw_event", "id", "linus_50point");
        assertArea(early, new int[]{-8, 63, -29}, new int[]{69, 83, 46});
        assertPrecondition(early, "friendship", "min", 50);
        assertPrecondition(early, "days_played", "min", 8);
        assertPrecondition(early, "time", "min", 2000);
        assertPrecondition(early, "time", "max", 2400);

        assertEquals("linus_4heart", four.get("id").getAsString());
        assertLegacyId(four, "26");
        assertPrecondition(four, "not_saw_event", "id", "linus_4heart");
        assertArea(four, new int[]{6, 80, -163}, new int[]{75, 98, -119});
        assertPrecondition(four, "friendship", "min", 1000);
        assertPrecondition(four, "time", "min", 2000);
        assertPrecondition(four, "time", "max", 2400);

        assertEquals("linus_8heart", eight.get("id").getAsString());
        assertLegacyId(eight, "371652");
        assertPrecondition(eight, "not_saw_event", "id", "linus_8heart");
        assertArea(eight, new int[]{6, 72, -163}, new int[]{77, 98, -78});
        assertPrecondition(eight, "friendship", "min", 2000);
        assertPrecondition(eight, "time", "min", 600);
        assertPrecondition(eight, "time", "max", 1700);

        assertFalse(preconditions(eight).asList().stream()
                .map(JsonElement::getAsJsonObject)
                .anyMatch(entry -> "saw_event".equals(entry.get("type").getAsString())),
                "Vanilla /a 12 26 is a tile condition, not a saw-event condition");
    }

    @Test
    void eventEffectsUseOnlyTheCapturedMinecraftAnchors() {
        JsonArray earlyCommands = event("linus_50point").getAsJsonArray("commands");
        assertTrue(hasPosition(earlyCommands, "particle", 40.5, 64.2, 2.5));
        assertTrue(hasPosition(earlyCommands, "particle", 33.5, 64.2, 12.5));

        JsonArray fourCommands = event("linus_4heart").getAsJsonArray("commands");
        assertTrue(hasPosition(fourCommands, "camera", 56.070, 87.307, -142.940));
        assertTrue(hasCommand(fourCommands, "add_recipe", "recipe", "wild_bait"));

        JsonArray eightCommands = event("linus_8heart").getAsJsonArray("commands");
        assertTrue(hasPosition(eightCommands, "camera", 19.334, 91.799, -108.988));
        assertTrue(hasPosition(eightCommands, "camera", 34.758, 88.425, -98.298));
        assertTrue(hasNestedCommand(eightCommands, "add_friendship", "points", 250));
    }

    @Test
    void wildBaitRecipeMatchesVanillaAndRequiresExplicitUnlock() {
        JsonArray recipes = json(
                "data/stardewcraft/player/vanilla_crafting_recipes.json")
                .getAsJsonArray("recipes");
        JsonObject recipe = recipes.asList().stream()
                .map(JsonElement::getAsJsonObject)
                .filter(entry -> "wild_bait".equals(entry.get("id").getAsString()))
                .findFirst()
                .orElseThrow();

        assertEquals("null", recipe.get("unlockCondition").getAsString());
        assertEquals("stardewcraft:wild_bait",
                recipe.getAsJsonObject("output").get("item").getAsString());
        assertEquals(5, recipe.getAsJsonObject("output").get("count").getAsInt());
        assertIngredient(recipe, "stardewcraft:fiber", 10);
        assertIngredient(recipe, "stardewcraft:bug_meat", 5);
        assertIngredient(recipe, "stardewcraft:slime_item", 5);
    }

    @Test
    void everySupportedLanguageContainsEveryImportedLinusEventLine() {
        List<String> required = List.of(
                "event.linus.502969.george_raccoons",
                "event.linus.502969.question",
                "event.linus.502969.choice.1",
                "event.linus.502969.response.4",
                "event.linus.502969.gus_food",
                "event.linus.26.firepit",
                "event.linus.26.wild_bait",
                "event.linus.26.recipe_learned",
                "event.linus.371652.robin_greeting",
                "event.linus.371652.choice.well",
                "event.linus.371652.choice.farm",
                "event.linus.371652.linus_well",
                "event.linus.371652.linus_dont_help",
                "event.linus.371652.robin_sweetheart");
        for (String language : LANGUAGES) {
            JsonObject lang = json("assets/stardewcraft/lang/" + language + ".json");
            for (String key : required) {
                assertTrue(lang.has(key), language + " is missing " + key);
                assertFalse(lang.get(key).getAsString().isBlank(),
                        language + " has blank " + key);
            }
        }
    }

    @Test
    void eventsUseTheExactImportedVanillaAudioCues() throws Exception {
        JsonObject sounds = json("assets/stardewcraft/sounds.json");
        assertEquals("stardewcraft:music/night_time",
                sounds.getAsJsonObject("music_night_time")
                        .getAsJsonArray("sounds").get(0).getAsJsonObject()
                        .get("name").getAsString());
        assertEquals("stardewcraft:music/echos",
                sounds.getAsJsonObject("music_echos")
                        .getAsJsonArray("sounds").get(0).getAsJsonObject()
                        .get("name").getAsString());
        assertEquals("stardewcraft:music/sweet",
                sounds.getAsJsonObject("music_sweet")
                        .getAsJsonArray("sounds").get(0).getAsJsonObject()
                        .get("name").getAsString());
        assertEquals(2, sounds.getAsJsonObject("dirty_hit")
                .getAsJsonArray("sounds").size());
        assertEquals(2, sounds.getAsJsonObject("wood_whack")
                .getAsJsonArray("sounds").size());

        for (String path : List.of(
                "assets/stardewcraft/sounds/music/night_time.ogg",
                "assets/stardewcraft/sounds/music/echos.ogg",
                "assets/stardewcraft/sounds/music/sweet.ogg",
                "assets/stardewcraft/sounds/event/linus/grassy_step.ogg",
                "assets/stardewcraft/sounds/event/linus/dirty_hit_1.ogg",
                "assets/stardewcraft/sounds/event/linus/dirty_hit_2.ogg",
                "assets/stardewcraft/sounds/event/linus/wood_whack_1.ogg",
                "assets/stardewcraft/sounds/event/linus/wood_whack_2.ogg")) {
            try (InputStream stream = resource(path)) {
                assertTrue(stream.readNBytes(4).length == 4, path);
            }
        }
    }

    private static void assertArea(JsonObject event, int[] min, int[] max) {
        JsonObject trigger = event.getAsJsonObject("trigger");
        assertArrayEquals(min, ints(trigger.getAsJsonArray("area_min")));
        assertArrayEquals(max, ints(trigger.getAsJsonArray("area_max")));
    }

    private static int[] ints(JsonArray values) {
        int[] result = new int[values.size()];
        for (int index = 0; index < values.size(); index++) {
            result[index] = values.get(index).getAsInt();
        }
        return result;
    }

    private static JsonArray preconditions(JsonObject event) {
        return event.getAsJsonArray("preconditions");
    }

    private static void assertPrecondition(
            JsonObject event, String type, String field, int value) {
        assertTrue(preconditions(event).asList().stream()
                .map(JsonElement::getAsJsonObject)
                .anyMatch(entry -> type.equals(entry.get("type").getAsString())
                        && entry.has(field)
                        && entry.get(field).getAsInt() == value),
                () -> event.get("id") + " missing " + type + "." + field + "=" + value);
    }

    private static void assertPrecondition(
            JsonObject event, String type, String field, String value) {
        assertTrue(preconditions(event).asList().stream()
                        .map(JsonElement::getAsJsonObject)
                        .anyMatch(entry -> type.equals(entry.get("type").getAsString())
                                && entry.has(field)
                                && value.equals(entry.get(field).getAsString())),
                () -> event.get("id") + " missing " + type + "." + field + "=" + value);
    }

    private static void assertLegacyId(JsonObject event, String legacyId) {
        assertTrue(event.getAsJsonArray("legacy_ids").asList().stream()
                        .map(JsonElement::getAsString)
                        .anyMatch(legacyId::equals),
                () -> event.get("id") + " missing legacy ID " + legacyId);
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

    private static boolean hasNestedCommand(
            JsonArray commands, String command, String field, int value) {
        for (JsonElement element : commands) {
            JsonObject entry = element.getAsJsonObject();
            if (command.equals(entry.get("cmd").getAsString())
                    && entry.has(field) && entry.get(field).getAsInt() == value) {
                return true;
            }
            if (entry.has("commands")
                    && hasNestedCommand(entry.getAsJsonArray("commands"), command, field, value)) {
                return true;
            }
            if (entry.has("choices")) {
                for (JsonElement choice : entry.getAsJsonArray("choices")) {
                    if (hasNestedCommand(
                            choice.getAsJsonObject().getAsJsonArray("commands"),
                            command, field, value)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static void assertIngredient(JsonObject recipe, String item, int count) {
        assertTrue(recipe.getAsJsonArray("ingredients").asList().stream()
                .map(JsonElement::getAsJsonObject)
                .anyMatch(entry -> item.equals(entry.get("item").getAsString())
                        && entry.get("count").getAsInt() == count));
    }

    private static JsonObject event(String file) {
        return json("data/stardewcraft/cutscene_events/" + file + ".json");
    }

    private static JsonObject json(String path) {
        InputStream stream = resource(path);
        return JsonParser.parseReader(new InputStreamReader(
                stream, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static InputStream resource(String path) {
        InputStream stream = LinusHeartEventResourceTest.class
                .getClassLoader().getResourceAsStream(path);
        assertNotNull(stream, path);
        return stream;
    }
}
