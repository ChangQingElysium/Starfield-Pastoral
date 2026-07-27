package com.stardew.craft.cooking.service;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VanillaCookingRecipeDataAtomicSnapshotTest {
    @Test
    void definitionsRuntimeLookupAndSyncJsonPublishTogether() {
        String previous = VanillaCookingRecipeData.getCachedJson();
        ResourceLocation firstId = ResourceLocation.fromNamespaceAndPath(
                "cooking_snapshot_test", "first");
        try {
            VanillaCookingRecipeData.applyFromJson("""
                    {
                      "cooking_snapshot_test:first": {
                        "output": "minecraft:pumpkin_pie",
                        "ingredients": [
                          {"item": "minecraft:pumpkin", "count": 1}
                        ]
                      }
                    }
                    """);

            VanillaCookingRecipeData.Catalog accepted =
                    VanillaCookingRecipeData.catalog();
            assertCoherent(accepted);
            assertTrue(VanillaCookingRecipeData
                    .getDefinition(firstId).isPresent());

            VanillaCookingRecipeData.applyFromJson("""
                    {
                      "cooking_snapshot_test:invalid": {
                        "ingredients": [
                          {"item": "minecraft:carrot", "count": 1}
                        ]
                      }
                    }
                    """);

            assertSame(accepted,
                    VanillaCookingRecipeData.catalog(),
                    "an invalid cooking sync replaced the accepted catalog");
            assertTrue(VanillaCookingRecipeData
                    .getDefinition(firstId).isPresent());

            VanillaCookingRecipeData.applyFromJson("""
                    {
                      "cooking_snapshot_test:second": {
                        "output": "minecraft:golden_carrot",
                        "ingredients": [
                          {"item": "minecraft:carrot", "count": 1}
                        ]
                      }
                    }
                    """);

            VanillaCookingRecipeData.Catalog replacement =
                    VanillaCookingRecipeData.catalog();
            assertCoherent(replacement);
            assertTrue(replacement.definitions().version()
                    > accepted.definitions().version());
        } finally {
            VanillaCookingRecipeData.applyFromJson(previous);
        }
    }

    private static void assertCoherent(
            VanillaCookingRecipeData.Catalog catalog
    ) {
        assertSame(catalog.definitions(),
                VanillaCookingRecipeData.snapshot());
        assertEquals(
                catalog.recipes().keySet(),
                JsonParser.parseString(catalog.cachedJson())
                        .getAsJsonObject()
                        .keySet().stream()
                        .map(ResourceLocation::parse)
                        .collect(java.util.stream.Collectors.toSet()));
        assertEquals(
                catalog.recipes().keySet(),
                java.util.Set.copyOf(
                        VanillaCookingRecipeData.getRecipeIds()));
    }
}
