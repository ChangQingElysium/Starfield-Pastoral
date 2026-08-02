package com.stardew.craft.npc;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LinusScheduleDataTest {
    @Test
    void usesAllConfirmedContinuousSchedulePoints() throws Exception {
        JsonObject points = load("data/stardewcraft/npc/events/npc_route_points.json")
                .getAsJsonObject("points");
        int[][] coordinates = {
                {45, 85, -160}, {36, 85, -155}, {53, 90, -131}, {62, 86, -144},
                {54, 85, -146}, {50, 86, -146}, {70, 82, -107}, {79, 81, -102},
                {75, 81, -116}, {75, 81, -114}, {51, 85, -156}, {52, 85, -155},
                {51, 85, -152}, {53, 85, -153}, {-6, 85, -173}, {-19, 85, -176},
                {43, 60, 94}, {-203, 64, -205}
        };

        for (int index = 0; index < coordinates.length; index++) {
            String pointId = "linus_schedule_%02d".formatted(index + 1);
            JsonObject point = points.getAsJsonObject(pointId);
            assertNotNull(point, "missing " + pointId);
            assertEquals(coordinates[index][0], point.get("x").getAsInt(), pointId + " x");
            assertEquals(coordinates[index][1], point.get("y").getAsInt(), pointId + " y");
            assertEquals(coordinates[index][2], point.get("z").getAsInt(), pointId + " z");
            assertFalse(point.get("indoor").getAsBoolean(), pointId + " must be outdoors");
        }
    }

    @Test
    void mirrorsVanillaScheduleTimesFacingsAndBehaviors() throws Exception {
        JsonObject schedule = load("data/stardewcraft/npc/schedules/linus.json");
        Map<String, Map<String, String>> expected = new LinkedHashMap<>();
        expected.put("rain", Map.of(
                "700", "tent @linus_schedule_11 0",
                "930", "mountain @linus_schedule_01 1",
                "1010", "tent @linus_schedule_12 2",
                "1500", "mountain @linus_schedule_02 2",
                "1900", "tent @linus_schedule_11 2"));
        expected.put("GreenRain", Map.of(
                "610", "mountain @linus_schedule_03 2",
                "1200", "mountain @linus_schedule_04 1",
                "1700", "mountain @linus_schedule_05 3",
                "2200", "tent @linus_schedule_14 2 linus_sleep"));
        expected.put("DesertFestival_2", Map.of(
                "610", "tent @linus_schedule_11 0",
                "700", "desert @linus_schedule_18 1 dialogue",
                "2540", "tent @linus_schedule_14 2 linus_sleep"));
        expected.put("winter_15", Map.of(
                "1100", "mountain @linus_schedule_06 1",
                "1600", "beach @linus_schedule_17 2 dialogue",
                "2330", "tent @linus_schedule_14 2 linus_sleep"));
        expected.put("summer", Map.of(
                "630", "mountain @linus_schedule_04 1",
                "940", "mountain @linus_schedule_07 1 square_3_3",
                "1300", "mountain @linus_schedule_08 2 square_3_3",
                "1600", "mountain @linus_schedule_06 1",
                "2000", "mountain @linus_schedule_01 1",
                "2020", "tent @linus_schedule_11 2"));
        expected.put("fall", Map.of(
                "700", "mountain @linus_schedule_01 1",
                "740", "mountain @linus_schedule_06 1",
                "900", "railroad @linus_schedule_15 2 square_5_5",
                "1400", "mountain @linus_schedule_09 1",
                "1800", "tent @linus_schedule_11 2"));
        expected.put("winter", Map.of(
                "1100", "mountain @linus_schedule_06 1",
                "1400", "bathhouse_entry @linus_schedule_16 3 dialogue",
                "1800", "tent @linus_schedule_11 2"));
        expected.put("spring", Map.of(
                "630", "mountain @linus_schedule_01 1",
                "700", "mountain @linus_schedule_06 1",
                "930", "mountain @linus_schedule_10 1",
                "1400", "mountain @linus_schedule_06 1",
                "1900", "tent @linus_schedule_12 1",
                "2100", "tent @linus_schedule_13 2",
                "2300", "tent @linus_schedule_14 2 linus_sleep"));

        assertEquals(expected.keySet(), schedule.entrySet().stream()
                .filter(entry -> entry.getValue().isJsonObject())
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new)));
        expected.forEach((scheduleKey, entries) -> {
            JsonObject actual = schedule.getAsJsonObject(scheduleKey);
            assertEquals(entries.size(), actual.size(), scheduleKey + " entry count");
            entries.forEach((time, value) -> assertEquals(value, actual.get(time).getAsString(),
                    scheduleKey + " at " + time));
        });
    }

    private static JsonObject load(String path) throws Exception {
        try (var stream = LinusScheduleDataTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream, "missing resource " + path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        }
    }
}
