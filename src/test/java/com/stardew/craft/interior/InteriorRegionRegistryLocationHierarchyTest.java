package com.stardew.craft.interior;

import com.stardew.craft.api.v1.world.StardewLocationEnvironmentKeys;
import com.stardew.craft.api.v1.world.StardewLocations;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InteriorRegionRegistryLocationHierarchyTest {
    @Test
    void hierarchyInheritsEnvironmentAndRejectedReloadKeepsSnapshot() {
        String previous = InteriorRegionRegistry.getCachedJson();
        ResourceLocation parent = id("valley");
        ResourceLocation child = id("orchard");
        ResourceLocation fruit = id("fruit");
        try {
            InteriorRegionRegistry.applyFromJson("""
                    {
                      "location_hierarchy_test:valley": {
                        "dimension": "minecraft:overworld",
                        "min": [0, 0, 0],
                        "max": [100, 100, 100],
                        "display_name": "Test Valley",
                        "tags": ["location_hierarchy_test:temperate"],
                        "properties": {
                          "stardewcraft:climate": "temperate",
                          "location_hierarchy_test:fruit": "pear"
                        }
                      },
                      "location_hierarchy_test:orchard": {
                        "dimension": "minecraft:overworld",
                        "min": [10, 0, 10],
                        "max": [20, 20, 20],
                        "priority": 20,
                        "indoor": true,
                        "parent": "location_hierarchy_test:valley",
                        "display_name": "Test Orchard",
                        "tags": ["location_hierarchy_test:orchard"],
                        "properties": {
                          "location_hierarchy_test:fruit": "apple"
                        }
                      }
                    }
                    """);

            var orchard = StardewLocations.get(child).orElseThrow();
            assertEquals(parent, orchard.parentId());
            assertEquals("apple",
                    orchard.property(fruit).orElseThrow());
            assertEquals("temperate",
                    orchard.property(
                            StardewLocationEnvironmentKeys.CLIMATE)
                            .orElseThrow());
            assertTrue(orchard.hasTag(id("temperate")));
            assertTrue(orchard.hasTag(
                    StardewLocationEnvironmentKeys.INDOOR));
            assertFalse(orchard.hasTag(
                    StardewLocationEnvironmentKeys.OUTDOOR));
            assertEquals(
                    java.util.List.of(child, parent),
                    StardewLocations.hierarchy(child).stream()
                            .map(location -> location.id())
                            .toList());
            assertTrue(StardewLocations.isWithin(child, parent));
            assertCoherent(InteriorRegionRegistry.catalog());
            long acceptedVersion = InteriorRegionRegistry
                    .catalog().definitions().version();

            InteriorRegionRegistry.applyFromJson("""
                    {
                      "location_hierarchy_test:a": {
                        "dimension": "minecraft:overworld",
                        "min": [0, 0, 0],
                        "max": [1, 1, 1],
                        "parent": "location_hierarchy_test:b"
                      },
                      "location_hierarchy_test:b": {
                        "dimension": "minecraft:overworld",
                        "min": [0, 0, 0],
                        "max": [1, 1, 1],
                        "parent": "location_hierarchy_test:a"
                      }
                    }
                    """);
            assertTrue(StardewLocations.get(child).isPresent(),
                    "rejected parent cycle replaced the active snapshot");
            assertEquals(acceptedVersion, InteriorRegionRegistry
                    .catalog().definitions().version());
            assertCoherent(InteriorRegionRegistry.catalog());
        } finally {
            InteriorRegionRegistry.applyFromJson(previous);
        }
    }

    private static void assertCoherent(
            InteriorRegionRegistry.Catalog catalog
    ) {
        assertEquals(catalog.definitions(),
                InteriorRegionRegistry.snapshot());
        Set<ResourceLocation> definitionIds =
                catalog.definitions().definitions().keySet();
        assertEquals(definitionIds,
                catalog.locationsById().keySet());
        assertEquals(definitionIds,
                catalog.locations().stream()
                        .map(location -> location.id())
                        .collect(java.util.stream.Collectors.toSet()));
        catalog.locationAliases().values().forEach(id ->
                assertTrue(catalog.locationsById().containsKey(id)));
        long expectedIndoor = catalog.definitions().definitions()
                .values().stream()
                .filter(definition -> definition.indoor())
                .count();
        assertEquals(expectedIndoor, catalog.regions().size());
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                "location_hierarchy_test", path);
    }
}
