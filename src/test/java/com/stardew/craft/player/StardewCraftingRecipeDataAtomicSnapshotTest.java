package com.stardew.craft.player;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StardewCraftingRecipeDataAtomicSnapshotTest {
    @Test
    void definitionsIndexesAndSyncJsonPublishTogether() {
        String previous = StardewCraftingRecipeData.getCachedJson();
        ResourceLocation firstId = ResourceLocation.fromNamespaceAndPath(
                "crafting_snapshot_test", "first");
        try {
            StardewCraftingRecipeData.applyFromJson("""
                    {
                      "crafting_snapshot_test:first": {
                        "output": "minecraft:chest",
                        "ingredients": [
                          {"item": "minecraft:oak_planks", "count": 8}
                        ],
                        "big_craftable": true
                      }
                    }
                    """);

            StardewCraftingRecipeData.Catalog accepted =
                    StardewCraftingRecipeData.catalog();
            assertCoherent(accepted);
            assertTrue(StardewCraftingRecipeData.isBigCraftable(
                    firstId.toString()));
            assertTrue(StardewCraftingRecipeData.getRecipe(
                    firstId.toString()).isPresent());

            StardewCraftingRecipeData.applyFromJson("""
                    {
                      "crafting_snapshot_test:invalid": {
                        "ingredients": [
                          {"item": "minecraft:stick"}
                        ],
                        "big_craftable": true
                      }
                    }
                    """);

            assertSame(accepted, StardewCraftingRecipeData.catalog(),
                    "a rejected sync replaced the accepted catalog");
            assertTrue(StardewCraftingRecipeData.getRecipe(
                    firstId.toString()).isPresent());

            StardewCraftingRecipeData.applyFromJson("""
                    {
                      "crafting_snapshot_test:second": {
                        "output": "minecraft:barrel",
                        "ingredients": [
                          {"item": "minecraft:oak_planks", "count": 6}
                        ]
                      }
                    }
                    """);

            StardewCraftingRecipeData.Catalog replacement =
                    StardewCraftingRecipeData.catalog();
            assertCoherent(replacement);
            assertTrue(replacement.definitions().version()
                    > accepted.definitions().version());
            assertFalse(StardewCraftingRecipeData.isBigCraftable(
                    "crafting_snapshot_test:second"));
            assertFalse(StardewCraftingRecipeData.getRecipe(
                    firstId.toString()).isPresent());
        } finally {
            StardewCraftingRecipeData.applyFromJson(previous);
        }
    }

    private static void assertCoherent(
            StardewCraftingRecipeData.Catalog catalog
    ) {
        assertSame(catalog.definitions(),
                StardewCraftingRecipeData.snapshot());
        assertEquals(catalog.definitions().definitions(),
                catalog.recipes());
        assertTrue(catalog.recipes().keySet()
                .containsAll(catalog.bigCraftableRecipes()));

        JsonObject cached = JsonParser.parseString(
                catalog.cachedJson()).getAsJsonObject();
        assertEquals(
                catalog.recipes().keySet(),
                cached.keySet().stream()
                        .map(ResourceLocation::parse)
                        .collect(java.util.stream.Collectors.toSet()));
        Set<ResourceLocation> encodedBigCraftables =
                cached.entrySet().stream()
                        .filter(entry -> entry.getValue().isJsonObject())
                        .filter(entry -> entry.getValue()
                                .getAsJsonObject()
                                .has("big_craftable"))
                        .filter(entry -> entry.getValue()
                                .getAsJsonObject()
                                .get("big_craftable")
                                .getAsBoolean())
                        .map(entry -> ResourceLocation.parse(entry.getKey()))
                        .collect(java.util.stream.Collectors.toSet());
        assertEquals(catalog.bigCraftableRecipes(),
                encodedBigCraftables);
    }
}
