package com.stardew.craft.building;

import com.google.gson.JsonParser;
import com.stardew.craft.api.v1.building.StardewBuildingBlueprint;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildingBlueprintRegistryTest {
    @Test
    void datapackBlueprintDecodesStableCatalogMetadata() {
        var definition = BuildingBlueprintRegistry.decode(
                id("orchard_storehouse"),
                JsonParser.parseString(validJson()));

        assertEquals(id("orchard_carpenter"),
                definition.builder());
        assertEquals(2, definition.materials().size());
        assertEquals(
                ResourceLocation.fromNamespaceAndPath(
                        "minecraft", "barrel"),
                definition.resultItem());
        assertTrue(definition.tags().contains(id("orchard")));
    }

    @Test
    void decoderRejectsTyposDuplicateMaterialsAndUnknownItems() {
        assertThrows(IllegalArgumentException.class, () ->
                BuildingBlueprintRegistry.decode(
                        id("typo"),
                        JsonParser.parseString(validJson()
                                .replace(
                                        "\"money\": 2500",
                                        "\"money\": 2500, \"monye\": 2"))));
        assertThrows(IllegalArgumentException.class, () ->
                BuildingBlueprintRegistry.decode(
                        id("duplicate"),
                        JsonParser.parseString(validJson()
                                .replace(
                                        "{ \"item\": \"minecraft:stone\", \"count\": 32 }",
                                        "{ \"item\": \"minecraft:oak_log\", \"count\": 32 }"))));
        assertThrows(IllegalArgumentException.class, () ->
                BuildingBlueprintRegistry.decode(
                        id("missing"),
                        JsonParser.parseString(validJson()
                                .replace(
                                        "minecraft:barrel",
                                        "building_test:not_registered"))));
    }

    @Test
    void buildingCatalogPublishesAsOneCoherentSnapshot()
            throws Exception {
        BuildingBlueprintRegistry.Catalog before =
                BuildingBlueprintRegistry.catalog();
        var first = BuildingBlueprintRegistry.decode(
                id("coherent_first"),
                JsonParser.parseString(validJson()));
        var second = BuildingBlueprintRegistry.decode(
                id("coherent_second"),
                JsonParser.parseString(validJson()));
        Thread writer = null;
        try {
            BuildingBlueprintRegistry.publish(
                    Map.of(id("coherent_first"), first));
            AtomicBoolean running = new AtomicBoolean(true);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            writer = new Thread(() -> {
                try {
                    for (int index = 0; index < 2_000; index++) {
                        if ((index & 1) == 0) {
                            BuildingBlueprintRegistry.publish(
                                    Map.of(id("coherent_second"),
                                            second));
                        } else {
                            BuildingBlueprintRegistry.publish(
                                    Map.of(id("coherent_first"),
                                            first));
                        }
                    }
                } catch (Throwable throwable) {
                    failure.set(throwable);
                } finally {
                    running.set(false);
                }
            }, "building-blueprint-catalog-writer");
            writer.start();

            while (running.get()) {
                assertCoherent(BuildingBlueprintRegistry.catalog());
            }
            writer.join();

            assertNull(failure.get());
            assertCoherent(BuildingBlueprintRegistry.catalog());
        } finally {
            if (writer != null) {
                writer.join();
            }
            BuildingBlueprintRegistry.publish(before.data());
        }
    }

    private static void assertCoherent(
            BuildingBlueprintRegistry.Catalog catalog
    ) {
        assertTrue(catalog.revision() > 0);
        assertEquals(
                catalog.byId().keySet(),
                catalog.ordered().stream()
                        .map(StardewBuildingBlueprint::id)
                        .collect(java.util.stream.Collectors.toSet()));
        catalog.data().forEach((id, definition) -> {
            StardewBuildingBlueprint blueprint =
                    catalog.byId().get(id);
            assertEquals(id, blueprint.id());
            assertEquals(definition, blueprint.definition());
        });
    }

    private static String validJson() {
        return """
                {
                  "builder": "building_test:orchard_carpenter",
                  "order": 10,
                  "display_name": "building.building_test.storehouse",
                  "description": "building.building_test.storehouse.desc",
                  "money": 2500,
                  "materials": [
                    { "item": "minecraft:oak_log", "count": 64 },
                    { "item": "minecraft:stone", "count": 32 }
                  ],
                  "result_item": "minecraft:barrel",
                  "tags": ["building_test:orchard"],
                  "properties": {
                    "building_test:placement_hint": "orchard"
                  }
                }
                """;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                "building_test", path);
    }
}
