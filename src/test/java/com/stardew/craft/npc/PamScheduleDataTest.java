package com.stardew.craft.npc;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PamScheduleDataTest {
    @Test
    void keepsVanillaPamScheduleKeysAndBusHours() throws Exception {
        JsonObject root = loadPamSchedule();
        Set<String> expected = Set.of(
                "rain", "GreenRain", "SquidFest", "DesertFestival_2",
                "DesertFestival", "spring_25", "bus", "spring", "JojaMart_Replacement"
        );
        for (String key : expected) {
            assertTrue(root.has(key), "missing Pam schedule key " + key);
        }

        JsonObject bus = root.getAsJsonObject("bus");
        assertTrue(bus.get("830").getAsString().contains("@pam_busstop_bench"));
        assertTrue(bus.has("1700"));
        assertFalse(bus.has("1400"));
    }

    @Test
    void preservesPamDriverAndSelfDriveDayShapes() throws Exception {
        JsonObject root = loadPamSchedule();
        assertEquals("spring", root.getAsJsonObject("rain").get("_goto").getAsString());
        assertTrue(root.getAsJsonObject("GreenRain").get("0").getAsString().startsWith("saloon "));
        assertTrue(visitsBusStop(root.getAsJsonObject("DesertFestival")));
        assertFalse(visitsBusStop(root.getAsJsonObject("DesertFestival_2")));
        assertFalse(visitsBusStop(root.getAsJsonObject("SquidFest")));
        assertFalse(visitsBusStop(root.getAsJsonObject("spring_25")));
    }

    private static boolean visitsBusStop(JsonObject schedule) {
        return schedule.entrySet().stream()
                .filter(entry -> !entry.getKey().startsWith("_"))
                .anyMatch(entry -> entry.getValue().getAsString().contains("@pam_busstop_bench"));
    }

    private static JsonObject loadPamSchedule() throws Exception {
        try (var stream = PamScheduleDataTest.class.getClassLoader()
                .getResourceAsStream("data/stardewcraft/npc/schedules/pam.json")) {
            assertNotNull(stream);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        }
    }
}
