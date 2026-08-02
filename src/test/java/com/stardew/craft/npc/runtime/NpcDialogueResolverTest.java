package com.stardew.craft.npc.runtime;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class NpcDialogueResolverTest {
    @Test
    void sunnyWeatherNeverCollidesWithSundayDialogue() {
        JsonObject haley = resource("data/stardewcraft/npc/dialogue/haley.json");

        assertEquals("Mon", select(haley, context("haley", 1, "spring", "Mon", 0)).key());
        assertEquals("Tue", select(haley, context("haley", 2, "spring", "Tue", 0)).key());
        assertEquals("Wed", select(haley, context("haley", 3, "spring", "Wed", 0)).key());
        assertEquals("Thu", select(haley, context("haley", 4, "spring", "Thu", 0)).key());
        assertEquals("Fri", select(haley, context("haley", 5, "spring", "Fri", 0)).key());
        assertEquals("Sat", select(haley, context("haley", 6, "spring", "Sat", 0)).key());
        assertEquals("Sun", select(haley, context("haley", 7, "spring", "Sun", 0)).key());
    }

    @Test
    void seasonDateYearAndHeartPrecedenceMatchesNpcTryToRetrieveDialogue() {
        JsonObject entries = entries(
                "summer_10", "date-first-year",
                "summer_10_2", "date-year-two",
                "summer_Tue8_2", "heart-year",
                "summer_Tue8", "heart",
                "summer_Tue", "weekday",
                "Tue", "generic");

        NpcDialogueResolver.Context yearOne = context("test", 10, "summer", "Tue", 8);
        assertEquals("summer_10", select(entries, yearOne).key());

        NpcDialogueResolver.Context yearTwo = new NpcDialogueResolver.Context(
                "test", 10, 150, 2, "summer", "Tue", 8,
                false, false, false, false, "", 0, 0, false, false, List.of());
        assertEquals("summer_10_2", select(entries, yearTwo).key());

        NpcDialogueResolver.Context heartYear = new NpcDialogueResolver.Context(
                "test", 11, 151, 2, "summer", "Tue", 8,
                false, false, false, false, "", 0, 0, false, false, List.of());
        assertEquals("summer_Tue8_2", select(entries, heartYear).key());
    }

    @Test
    void activeAndLocationDialoguePrecedeDailyDialogue() {
        JsonObject entries = entries(
                "eventSeen_14", "event",
                "summer_Town_5_6", "tile",
                "summer_Town", "location",
                "summer_Mon", "daily");
        NpcDialogueResolver.Context active = new NpcDialogueResolver.Context(
                "haley", 1, 29, 1, "summer", "Mon", 0,
                false, false, false, false, "Town", 5, 6, false, false,
                List.of("eventSeen_14"));
        assertEquals("eventSeen_14", select(entries, active).key());

        NpcDialogueResolver.Context location = new NpcDialogueResolver.Context(
                "haley", 1, 29, 1, "summer", "Mon", 0,
                false, false, false, false, "Town", 5, 6, false, false, List.of());
        assertEquals("summer_Town_5_6", select(entries, location).key());
    }

    @Test
    void rainUsesSeparateVanillaRainySheetAndMissingDialogueHasNoArbitraryFallback() {
        JsonObject entries = entries("Sun", "sunday", "Introduction", "hello");
        JsonObject rainy = entries("Haley", "rain-line");
        NpcDialogueResolver.Context rainyDay = new NpcDialogueResolver.Context(
                "haley", 2, 2, 1, "spring", "Tue", 0,
                false, false, true, true, "", 0, 0, false, false, List.of());
        NpcDialogueResolver.Selection selection = NpcDialogueResolver.select(entries, rainy, rainyDay);
        assertEquals("Haley", selection.key());
        assertEquals(NpcDialogueResolver.Source.RAINY, selection.source());

        NpcDialogueResolver.Context missing = context("haley", 2, "spring", "Tue", 0);
        assertFalse(select(entries, missing).present());
    }

    @Test
    void vanillaSpecialCasesAreAppliedWithoutSuppressingDailyFallback() {
        JsonObject penny = entries(
                "fall_Mon4", "obsolete-heart-line",
                "fall_Mon", "house-upgrade-line");
        NpcDialogueResolver.Context pennyAfterUpgrade = new NpcDialogueResolver.Context(
                "penny", 1, 57, 1, "fall", "Mon", 4,
                false, false, false, false, "", 0, 0, true, false, List.of());
        assertEquals("fall_Mon", select(penny, pennyAfterUpgrade).key());

        JsonObject noGreenRainLine = entries(
                "eventSeen_14", "event",
                "summer_Mon", "daily");
        NpcDialogueResolver.Context greenRain = new NpcDialogueResolver.Context(
                "haley", 1, 29, 1, "summer", "Mon", 0,
                false, true, false, false, "Town", 5, 6, false, false,
                List.of("eventSeen_14"));
        assertEquals("summer_Mon", select(noGreenRainLine, greenRain).key());
    }

    private static NpcDialogueResolver.Selection select(
            JsonObject dialogue,
            NpcDialogueResolver.Context context
    ) {
        return NpcDialogueResolver.select(dialogue, null, context);
    }

    private static NpcDialogueResolver.Context context(
            String npcId,
            int day,
            String season,
            String weekday,
            int hearts
    ) {
        return new NpcDialogueResolver.Context(
                npcId, day, day, 1, season, weekday, hearts,
                false, false, false, false, "", 0, 0, false, false, List.of());
    }

    private static JsonObject entries(String... keyValues) {
        JsonObject entries = new JsonObject();
        for (int i = 0; i < keyValues.length; i += 2) {
            entries.addProperty(keyValues[i], keyValues[i + 1]);
        }
        JsonObject root = new JsonObject();
        root.add("entries", entries);
        return root;
    }

    private static JsonObject resource(String path) {
        try (var input = NpcDialogueResolverTest.class.getClassLoader().getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalStateException("Missing test resource " + path);
            }
            return JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
