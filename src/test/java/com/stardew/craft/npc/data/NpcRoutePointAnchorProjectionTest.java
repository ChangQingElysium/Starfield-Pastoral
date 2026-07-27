package com.stardew.craft.npc.data;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcRoutePointAnchorProjectionTest {
    @Test
    void legacyAndAddonRoutePointsProjectToNamespacedAnchors() {
        JsonObject corePoint = point(1, 64, 2);
        JsonObject coreRoot = root("town_square", corePoint);

        JsonObject addonPoint = point(8.25, 70, 9.75);
        addonPoint.addProperty("dimension", "minecraft:overworld");
        addonPoint.addProperty("indoor", true);
        addonPoint.addProperty("use_ground_height", true);
        addonPoint.addProperty("location", "orchard:market");
        JsonObject addonRoot = root("festival_stage", addonPoint);

        var anchors =
                NpcDataManager.ReloadListener.legacyRouteAnchors(
                        Map.of(
                                "npc_route_points", coreRoot,
                                "orchard:npc_route_points", addonRoot));

        var core = anchors.get(id("stardewcraft", "town_square"));
        assertEquals(1.5D, core.position().x);
        assertEquals(64.0D, core.position().y);
        assertEquals(2.5D, core.position().z);
        assertFalse(core.useGroundHeight());
        assertTrue(core.hasRole(
                id("stardewcraft", "npc_schedule")));

        var addon = anchors.get(
                id("orchard", "festival_stage"));
        assertEquals(id("minecraft", "overworld"),
                addon.dimension());
        assertEquals(8.25D, addon.position().x);
        assertTrue(addon.indoor());
        assertTrue(addon.useGroundHeight());
        assertEquals(id("orchard", "market"),
                addon.locationId());
    }

    private static JsonObject root(
            String pointId,
            JsonObject point
    ) {
        JsonObject points = new JsonObject();
        points.add(pointId, point);
        points.addProperty("_comment", "ignored");
        JsonObject root = new JsonObject();
        root.add("points", points);
        return root;
    }

    private static JsonObject point(
            Number x,
            Number y,
            Number z
    ) {
        JsonObject point = new JsonObject();
        point.addProperty("x", x);
        point.addProperty("y", y);
        point.addProperty("z", z);
        return point;
    }

    private static ResourceLocation id(
            String namespace,
            String path
    ) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }
}
