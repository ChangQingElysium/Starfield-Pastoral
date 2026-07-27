package com.stardew.craft.world;

import com.google.gson.JsonParser;
import com.stardew.craft.api.v1.world.StardewRegion;
import com.stardew.craft.api.v1.world.StardewRegions;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldRegionRegistryTest {
    @Test
    void compositeRegionsSupportUnionSubtractionTagsAndLocations() {
        StardewRegion region = WorldRegionRegistry.decode(
                id("orchard_shape"),
                JsonParser.parseString("""
                        {
                          "location": "orchard",
                          "include": [
                            {"min": [0, 60, 0], "max": [9, 70, 9]},
                            {"min": [20, 60, 20], "max": [29, 70, 29]}
                          ],
                          "exclude": {
                            "min": [4, 60, 4],
                            "max": [5, 70, 5]
                          },
                          "tags": ["outdoor", "stardewcraft:forage"],
                          "priority": 25
                        }
                        """));

        assertEquals(id("orchard"), region.locationId());
        assertEquals(id("outdoor"), region.tags().stream()
                .filter(tag -> tag.getPath().equals("outdoor"))
                .findFirst().orElseThrow());
        assertTrue(region.contains(dimension(), new BlockPos(1, 64, 1)));
        assertTrue(region.contains(
                dimension(), new BlockPos(22, 64, 22)));
        assertFalse(region.contains(
                dimension(), new BlockPos(4, 64, 4)));
        assertFalse(region.contains(
                dimension(), new BlockPos(15, 64, 15)));
        assertFalse(region.contains(
                ResourceLocation.fromNamespaceAndPath(
                        "minecraft", "overworld"),
                new BlockPos(1, 64, 1)));

        Map<ResourceLocation, StardewRegion> previous =
                new LinkedHashMap<>();
        StardewRegions.all().forEach(
                current -> previous.put(current.id(), current));
        StardewRegion lowerPriority = new StardewRegion(
                id("whole_area"),
                dimension(),
                null,
                java.util.List.of(new StardewRegion.Box(
                        new BlockPos(0, 0, 0),
                        new BlockPos(100, 100, 100))),
                java.util.List.of(),
                java.util.Set.of(id("outdoor")),
                -5);
        try {
            WorldRegionRegistry.publish(Map.of(
                    region.id(), region,
                    lowerPriority.id(), lowerPriority));

            assertEquals(region,
                    StardewRegions.get(region.id()).orElseThrow());
            assertEquals(
                    java.util.List.of(
                            region.id(), lowerPriority.id()),
                    StardewRegions.findAll(
                                    dimension(),
                                    new BlockPos(1, 64, 1))
                            .stream().map(StardewRegion::id).toList());
            assertEquals(java.util.List.of(region),
                    StardewRegions.forLocation(id("orchard")));
            assertEquals(
                    java.util.List.of(
                            region.id(), lowerPriority.id()),
                    StardewRegions.withTag(id("outdoor")).stream()
                            .map(StardewRegion::id).toList());
        } finally {
            WorldRegionRegistry.publish(previous);
        }
    }

    @Test
    void malformedOrEmptyGeometryIsRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                WorldRegionRegistry.decode(
                        id("empty"),
                        JsonParser.parseString("""
                                {"include": []}
                                """)));
        assertThrows(IllegalArgumentException.class, () ->
                WorldRegionRegistry.decode(
                        id("inverted"),
                        JsonParser.parseString("""
                                {
                                  "include": {
                                    "min": [2, 0, 0],
                                    "max": [1, 1, 1]
                                  }
                                }
                                """)));
    }

    @Test
    void regionIndexesPublishAsOneCoherentSnapshot() throws Exception {
        WorldRegionRegistry.Catalog before =
                WorldRegionRegistry.catalog();
        StardewRegion first = region(
                "first", "first_location", 10);
        StardewRegion second = region(
                "second", "second_location", 20);
        Thread writer = null;
        try {
            WorldRegionRegistry.publish(Map.of(first.id(), first));
            AtomicBoolean running = new AtomicBoolean(true);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            writer = new Thread(() -> {
                try {
                    for (int index = 0; index < 2_000; index++) {
                        StardewRegion value =
                                (index & 1) == 0 ? second : first;
                        WorldRegionRegistry.publish(
                                Map.of(value.id(), value));
                    }
                } catch (Throwable throwable) {
                    failure.set(throwable);
                } finally {
                    running.set(false);
                }
            }, "world-region-catalog-writer");
            writer.start();

            while (running.get()) {
                assertCoherent(WorldRegionRegistry.catalog());
            }
            writer.join();

            assertNull(failure.get());
            assertCoherent(WorldRegionRegistry.catalog());
        } finally {
            if (writer != null) {
                writer.join();
            }
            WorldRegionRegistry.publish(before.byId());
        }
    }

    private static void assertCoherent(
            WorldRegionRegistry.Catalog catalog
    ) {
        assertTrue(catalog.revision() > 0);
        assertEquals(catalog.byId().values().stream()
                        .map(StardewRegion::id)
                        .collect(java.util.stream.Collectors.toSet()),
                catalog.ordered().stream()
                        .map(StardewRegion::id)
                        .collect(java.util.stream.Collectors.toSet()));
        for (StardewRegion region : catalog.ordered()) {
            assertEquals(region,
                    catalog.byId().get(region.id()));
            if (region.locationId() != null) {
                assertTrue(catalog.byLocation()
                        .getOrDefault(
                                region.locationId(), List.of())
                        .contains(region));
            }
        }
        catalog.byLocation().forEach((locationId, regions) ->
                regions.forEach(region -> {
                    assertEquals(locationId, region.locationId());
                    assertEquals(region,
                            catalog.byId().get(region.id()));
                }));
    }

    private static StardewRegion region(
            String id,
            String location,
            int priority
    ) {
        return new StardewRegion(
                id(id),
                dimension(),
                id(location),
                List.of(new StardewRegion.Box(
                        new BlockPos(0, 0, 0),
                        new BlockPos(10, 10, 10))),
                List.of(),
                Set.of(id("outdoor")),
                priority);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                "region_test", path);
    }

    private static ResourceLocation dimension() {
        return ResourceLocation.fromNamespaceAndPath(
                "stardewcraft", "stardew_valley");
    }
}
