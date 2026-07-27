package com.stardew.craft.festival;

import com.google.gson.JsonParser;
import com.stardew.craft.api.v1.festival.StardewFestivalMapOverlay;
import com.stardew.craft.api.v1.festival.StardewFestivalMapOverlays;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FestivalMapOverlayDataTest {
    @Test
    void decodesOwnerRelativeIdsAndPublishesAtomically() {
        ResourceLocation id = id("orchard_festival");
        StardewFestivalMapOverlay overlay =
                FestivalMapOverlayRegistry.decodeData(
                        id,
                        JsonParser.parseString("""
                                {
                                  "location": "orchard",
                                  "origin_anchor": "orchard_stage",
                                  "festival_schematic": "festivals/orchard.schem",
                                  "bounds_min_offset": [-4, 0, -6],
                                  "bounds_max_offset": [40, 12, 34],
                                  "safe_position_offsets": [[2, 0, 2]],
                                  "cleanup_tagged_entities": false,
                                  "tree_clearance": {
                                    "horizontal_radius": 3,
                                    "up": 8,
                                    "down": 1
                                  }
                                }
                                """));

        assertEquals(id, overlay.id());
        assertEquals(id("orchard"), overlay.locationId());
        assertEquals(id("orchard_stage"), overlay.originAnchor());
        assertEquals(id("festivals/orchard.schem"),
                overlay.festivalSchematic());
        assertNull(overlay.baseSchematic());
        assertEquals(new BlockPos(-4, 0, -6),
                overlay.boundsMinOffset());
        assertEquals(java.util.List.of(new BlockPos(2, 0, 2)),
                overlay.safePositionOffsets());
        assertTrue(overlay.requiresBlackFade());
        assertTrue(overlay.cleanupDroppedItems());
        assertFalse(overlay.cleanupTaggedEntities());
        assertEquals(
                new StardewFestivalMapOverlay.TreeClearance(3, 8, 1),
                overlay.treeClearance());

        Map<ResourceLocation, StardewFestivalMapOverlay> previous =
                FestivalMapOverlayRegistry.dataSnapshot();
        try {
            assertTrue(FestivalMapOverlayRegistry.publishData(
                    Map.of(id, overlay)));
            assertEquals(overlay,
                    StardewFestivalMapOverlays.find(id).orElseThrow());
            assertTrue(StardewFestivalMapOverlays.allAddonOverlays()
                    .contains(overlay));

            StardewFestivalMapOverlay conflict =
                    javaOverlayConflict();
            assertFalse(FestivalMapOverlayRegistry.publishData(
                    Map.of(conflict.id(), conflict)));
            assertEquals(overlay,
                    StardewFestivalMapOverlays.find(id).orElseThrow());
        } finally {
            assertTrue(FestivalMapOverlayRegistry.publishData(previous));
        }
    }

    @Test
    void rejectsMalformedCoordinatesAndInvertedBounds() {
        assertThrows(IllegalArgumentException.class, () ->
                FestivalMapOverlayRegistry.decodeData(
                        id("fractional"),
                        JsonParser.parseString("""
                                {
                                  "location": "orchard",
                                  "origin_anchor": "stage",
                                  "festival_schematic": "festival.schem",
                                  "bounds_min_offset": [0.5, 0, 0],
                                  "bounds_max_offset": [1, 1, 1]
                                }
                                """)));
        assertThrows(IllegalArgumentException.class, () ->
                FestivalMapOverlayRegistry.decodeData(
                        id("inverted"),
                        JsonParser.parseString("""
                                {
                                  "location": "orchard",
                                  "origin_anchor": "stage",
                                  "festival_schematic": "festival.schem",
                                  "bounds_min_offset": [2, 0, 0],
                                  "bounds_max_offset": [1, 1, 1]
                                }
                                """)));
    }

    private static StardewFestivalMapOverlay javaOverlayConflict() {
        ResourceLocation conflictId = ResourceLocation.fromNamespaceAndPath(
                "festival_overlay_test", "java_conflict");
        if (StardewFestivalMapOverlays.find(conflictId).isEmpty()) {
            StardewFestivalMapOverlays.register(
                    new StardewFestivalMapOverlay(
                            conflictId,
                            id("orchard"),
                            id("stage"),
                            null,
                            id("festival.schem"),
                            BlockPos.ZERO,
                            BlockPos.ZERO,
                            java.util.List.of(),
                            true,
                            true,
                            true,
                            StardewFestivalMapOverlay.TreeClearance.NONE));
        }
        return new StardewFestivalMapOverlay(
                conflictId,
                id("orchard"),
                id("stage"),
                null,
                id("festival.schem"),
                BlockPos.ZERO,
                BlockPos.ZERO,
                java.util.List.of(),
                true,
                true,
                true,
                StardewFestivalMapOverlay.TreeClearance.NONE);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                "festival_overlay_test", path);
    }
}
