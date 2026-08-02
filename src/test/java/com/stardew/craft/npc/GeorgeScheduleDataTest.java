package com.stardew.craft.npc;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeorgeScheduleDataTest {
    @Test
    void usesEveryCapturedSchedulePointInContinuousSourceOrder() throws Exception {
        JsonObject points = load("data/stardewcraft/npc/events/npc_route_points.json")
                .getAsJsonObject("points");
        int[][] coordinates = {
                {56, 22, 3}, {42, 22, 2}, {39, 22, -18}, {40, 22, -2},
                {9, 43, -15}, {2, 43, -23}, {120, 60, 159}, {40, 64, -1},
                {54, 36, -13}, {51, 36, 2}
        };
        boolean[] indoors = {
                true, true, true, true, true, true, false, false, true, true
        };

        for (int index = 0; index < coordinates.length; index++) {
            String pointId = "george_schedule_%02d".formatted(index + 1);
            JsonObject point = points.getAsJsonObject(pointId);
            assertNotNull(point, "missing " + pointId);
            assertEquals(coordinates[index][0], point.get("x").getAsInt(), pointId + " x");
            assertEquals(coordinates[index][1], point.get("y").getAsInt(), pointId + " y");
            assertEquals(coordinates[index][2], point.get("z").getAsInt(), pointId + " z");
            assertEquals(indoors[index], point.get("indoor").getAsBoolean(), pointId + " indoor");
        }

        assertNotNull(points.getAsJsonObject("desert_festival_visit_george"),
                "George's existing Desert Festival point must be reused");
        assertFalse(points.has("night_market_day3_george"),
                "the pre-audit Night Market point must not shadow george_schedule_07");
        for (String stale : new String[]{
                "george_joshhouse_tv", "george_joshhouse_kitchen",
                "george_joshhouse_stove", "george_joshhouse_sleep",
                "george_town_bench", "george_seedshop_visit",
                "george_saloon_visit", "george_beach_spot"}) {
            assertFalse(points.has(stale), "stale pre-audit point remains: " + stale);
        }
    }

    @Test
    void mirrorsVanillaScheduleTimesWithCapturedMinecraftFacings() throws Exception {
        JsonObject schedule = load("data/stardewcraft/npc/schedules/george.json");
        Map<String, Map<String, String>> expected = new LinkedHashMap<>();
        expected.put("rain", Map.of(
                "630", "joshhouse @george_schedule_01 0",
                "1200", "joshhouse @george_schedule_02 3",
                "1500", "joshhouse @george_schedule_01 0",
                "2000", "joshhouse @george_schedule_03 2 george_sleep"));
        expected.put("GreenRain", Map.of(
                "0", "joshhouse @george_schedule_04 0"));
        expected.put("DesertFestival_3", Map.of(
                "610", "joshhouse @george_schedule_01 0",
                "1000", "desert @desert_festival_visit_george 0 dialogue:stardewcraft.npc.schedule.george.desert_festival",
                "2250", "joshhouse @george_schedule_03 2 george_sleep"));
        expected.put("23", Map.of(
                "630", "joshhouse @george_schedule_02 3 dialogue:stardewcraft.npc.schedule.george.23.000",
                "1030", "hospital @george_schedule_05 2 dialogue:stardewcraft.npc.schedule.george.23.001",
                "1330", "hospital @george_schedule_06 2 dialogue:stardewcraft.npc.schedule.george.23.002",
                "1600", "joshhouse @george_schedule_01 0",
                "2000", "joshhouse @george_schedule_03 2 george_sleep"));
        expected.put("winter_17", Map.of(
                "630", "joshhouse @george_schedule_01 0",
                "1200", "joshhouse @george_schedule_02 3",
                "1620", "beach @george_schedule_07 2 dialogue:stardewcraft.npc.schedule.george.winter_17.000",
                "2340", "joshhouse @george_schedule_03 2 george_sleep"));
        expected.put("summer_friday", Map.of(
                "630", "joshhouse @george_schedule_01 0",
                "1200", "town @george_schedule_08 2",
                "1500", "joshhouse @george_schedule_01 0",
                "2000", "joshhouse @george_schedule_03 2 george_sleep"));
        expected.put("sun", Map.of(
                "630", "joshhouse @george_schedule_01 0",
                "1000", "seedshop @george_schedule_09 0 dialogue:stardewcraft.npc.schedule.george.sun.000",
                "1400", "joshhouse @george_schedule_01 0",
                "2000", "joshhouse @george_schedule_03 2 george_sleep"));
        expected.put("spring", expected.get("rain"));

        expected.forEach((scheduleKey, entries) -> {
            JsonObject actual = schedule.getAsJsonObject(scheduleKey);
            assertNotNull(actual, "missing " + scheduleKey);
            assertEquals(entries.size(), actual.size(), scheduleKey + " entry count");
            entries.forEach((time, value) -> assertEquals(value, actual.get(time).getAsString(),
                    scheduleKey + " at " + time));
        });

        JsonObject sportsSunday = schedule.getAsJsonObject("sunday");
        assertEquals("MAIL saloonSportsRoom", sportsSunday.get("_condition").getAsString());
        assertEquals("saloon @george_schedule_10 0 dialogue:stardewcraft.npc.schedule.george.sun.001",
                sportsSunday.get("1100").getAsString());
    }

    @Test
    void routeProfileCoversEveryGeorgeDestination() throws Exception {
        JsonObject george = load("data/stardewcraft/npc/events/npc_route_profiles.json")
                .getAsJsonObject("profiles").getAsJsonObject("george");
        assertEndpoint(george, "joshhouse", "george_schedule_01");
        assertEndpoint(george, "hospital", "george_schedule_05");
        assertEndpoint(george, "beach", "george_schedule_07");
        assertEndpoint(george, "town", "george_schedule_08");
        assertEndpoint(george, "seedshop", "george_schedule_09");
        assertEndpoint(george, "saloon", "george_schedule_10");
        assertEndpoint(george, "desert", "desert_festival_visit_george");
    }

    @Test
    void everySupportedLanguageContainsEveryVanillaScheduleLine() throws Exception {
        List<String> languages = List.of(
                "de_de", "en_us", "es_es", "fr_fr", "hu_hu", "it_it",
                "ja_jp", "ko_kr", "pt_br", "ru_ru", "tr_tr", "zh_cn");
        List<String> keys = List.of(
                "stardewcraft.npc.schedule.george.23.000",
                "stardewcraft.npc.schedule.george.23.001",
                "stardewcraft.npc.schedule.george.23.002",
                "stardewcraft.npc.schedule.george.winter_17.000",
                "stardewcraft.npc.schedule.george.sun.000",
                "stardewcraft.npc.schedule.george.sun.001",
                "stardewcraft.npc.schedule.george.desert_festival");
        for (String language : languages) {
            JsonObject lang = load("assets/stardewcraft/lang/" + language + ".json");
            for (String key : keys) {
                assertTrue(lang.has(key), language + " is missing " + key);
                assertFalse(lang.get(key).getAsString().isBlank(),
                        language + " has blank " + key);
            }
        }
    }

    private static void assertEndpoint(
            JsonObject profile, String location, String pointId) {
        JsonArray route = profile.getAsJsonArray(location);
        assertNotNull(route, "missing route " + location);
        assertEquals(pointId,
                route.get(route.size() - 1).getAsJsonObject().get("point").getAsString(),
                location + " endpoint");
    }

    private static JsonObject load(String path) throws Exception {
        try (var stream = GeorgeScheduleDataTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream, "missing resource " + path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        }
    }
}
