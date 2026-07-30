package com.stardew.craft.npc;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrailerNpcRouteDataTest {
    @Test
    void trailerAnchorReusesExistingPlayerPortalEndpoints() throws Exception {
        JsonObject anchors = load("data/stardewcraft/npc/location_mappings/base_locations.json")
                .getAsJsonObject("anchors");
        JsonObject trailer = anchors.getAsJsonObject("trailer");

        assertTrue(trailer.get("indoor").getAsBoolean());
        assertFalse(trailer.get("use_ground_height").getAsBoolean());
        assertEquals("trailer_enter", trailer.get("portal_target").getAsString());
        assertEquals("trailer_outdoor_door", trailer.get("outdoor_door_point").getAsString());
        assertEquals("trailer_indoor_entry", trailer.get("indoor_entry_point").getAsString());
        assertEquals("trailer_indoor_exit", trailer.get("indoor_exit_point").getAsString());

        JsonObject points = load("data/stardewcraft/npc/events/npc_route_points.json")
                .getAsJsonObject("points");
        assertPoint(points.getAsJsonObject("trailer_outdoor_door"), 72, 64, 9, false);
        assertPoint(points.getAsJsonObject("trailer_indoor_entry"), 72, 35, 4, true);
        assertPoint(points.getAsJsonObject("trailer_indoor_exit"), 71, 35, 5, true);
    }

    @Test
    void existingTrailerInteriorRegionContainsPamRoute() throws Exception {
        JsonArray regions = load("data/stardewcraft/locations/fixed_interiors.json")
                .getAsJsonArray("regions");
        JsonObject trailer = null;
        for (var element : regions) {
            JsonObject candidate = element.getAsJsonObject();
            if ("trailer".equals(candidate.get("id").getAsString())) {
                trailer = candidate;
                break;
            }
        }
        assertNotNull(trailer);

        JsonArray min = trailer.getAsJsonArray("min");
        JsonArray max = trailer.getAsJsonArray("max");
        assertCoordinates(min, 59, 34, -3);
        assertCoordinates(max, 80, 40, 10);

        JsonObject points = load("data/stardewcraft/npc/events/npc_route_points.json")
                .getAsJsonObject("points");
        for (String pointId : new String[]{
                "trailer_indoor_entry",
                "trailer_indoor_exit",
                "pam_trailer_couch",
                "pam_trailer_kitchen",
                "pam_trailer_sleep"}) {
            JsonObject point = points.getAsJsonObject(pointId);
            assertNotNull(point, "missing route point " + pointId);
            assertTrue(point.get("indoor").getAsBoolean(), pointId + " must be indoor");
            assertWithin(pointId, point, min, max);
        }
    }

    @Test
    void pamUsesDoorWarpAndIndoorTargetWhenReturningHome() throws Exception {
        JsonObject profiles = load("data/stardewcraft/npc/events/npc_route_profiles.json")
                .getAsJsonObject("profiles");

        assertTrailerProfile(profiles.getAsJsonObject("pam"), "pam_trailer_sleep");
    }

    private static void assertTrailerProfile(JsonObject npcProfile, String finalPoint) {
        JsonArray route = npcProfile.getAsJsonArray("trailer");
        assertEquals(3, route.size());
        assertStep(route, 0, "walk", "trailer_outdoor_door");
        assertStep(route, 1, "warp", "trailer_indoor_entry");
        assertStep(route, 2, "walk", finalPoint);
    }

    private static void assertStep(JsonArray route, int index, String mode, String point) {
        JsonObject step = route.get(index).getAsJsonObject();
        assertEquals(mode, step.get("mode").getAsString());
        assertEquals(point, step.get("point").getAsString());
    }

    private static void assertPoint(JsonObject point, int x, int y, int z, boolean indoor) {
        assertNotNull(point);
        assertEquals(x, point.get("x").getAsInt());
        assertEquals(y, point.get("y").getAsInt());
        assertEquals(z, point.get("z").getAsInt());
        assertEquals(indoor, point.get("indoor").getAsBoolean());
    }

    private static void assertCoordinates(JsonArray coordinates, int x, int y, int z) {
        assertEquals(x, coordinates.get(0).getAsInt());
        assertEquals(y, coordinates.get(1).getAsInt());
        assertEquals(z, coordinates.get(2).getAsInt());
    }

    private static void assertWithin(String pointId, JsonObject point, JsonArray min, JsonArray max) {
        int[] coordinates = {point.get("x").getAsInt(), point.get("y").getAsInt(), point.get("z").getAsInt()};
        for (int axis = 0; axis < coordinates.length; axis++) {
            assertTrue(coordinates[axis] >= min.get(axis).getAsInt()
                            && coordinates[axis] <= max.get(axis).getAsInt(),
                    pointId + " axis " + axis + " lies outside the trailer interior bounds");
        }
    }

    private static JsonObject load(String path) throws Exception {
        try (var stream = TrailerNpcRouteDataTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream, "missing resource " + path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        }
    }
}
