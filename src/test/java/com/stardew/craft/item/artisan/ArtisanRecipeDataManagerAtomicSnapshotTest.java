package com.stardew.craft.item.artisan;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArtisanRecipeDataManagerAtomicSnapshotTest {
    @Test
    void definitionsMachineIndexAndSyncJsonPublishTogether() {
        String previous = ArtisanRecipeDataManager.getCachedJson();
        try {
            ArtisanRecipeDataManager.applyFromJson("""
                    {
                      "machine_snapshot_test:press": [{
                        "inputId": "minecraft:apple",
                        "inputTag": null,
                        "inputMode": "DEFAULT",
                        "outputId": "minecraft:golden_apple",
                        "outputCount": 1,
                        "minutes": 30,
                        "consumeCount": 1,
                        "keepInputQuality": false,
                        "outputQuality": -1,
                        "preserveType": null,
                        "seedMakerRule": null,
                        "outputMode": "FIXED"
                      }]
                    }
                    """);

            ArtisanRecipeDataManager.Catalog accepted =
                    ArtisanRecipeDataManager.catalog();
            assertCoherent(accepted);
            assertEquals(1, ArtisanRecipeDataManager
                    .getRecipes("machine_snapshot_test:press")
                    .size());

            ArtisanRecipeDataManager.applyFromJson("""
                    {
                      "machine_snapshot_test:broken": 42
                    }
                    """);

            assertSame(accepted,
                    ArtisanRecipeDataManager.catalog(),
                    "an invalid network document replaced the accepted catalog");

            ArtisanRecipeDataManager.applyFromJson("""
                    {
                      "machine_snapshot_test:smoker": [{
                        "inputId": null,
                        "inputTag": null,
                        "inputMode": "FISH_TYPE",
                        "outputId": null,
                        "outputCount": 1,
                        "minutes": 50,
                        "consumeCount": 1,
                        "keepInputQuality": true,
                        "outputQuality": -1,
                        "preserveType": null,
                        "seedMakerRule": null,
                        "outputMode": "SMOKED"
                      }]
                    }
                    """);

            ArtisanRecipeDataManager.Catalog replacement =
                    ArtisanRecipeDataManager.catalog();
            assertCoherent(replacement);
            assertTrue(replacement.definitions().version()
                    > accepted.definitions().version());
            assertEquals(
                    Set.of("machine_snapshot_test:smoker"),
                    ArtisanRecipeDataManager.getAllMachineKeys());
        } finally {
            ArtisanRecipeDataManager.applyFromJson(
                    previous.isBlank() ? "{}" : previous);
        }
    }

    private static void assertCoherent(
            ArtisanRecipeDataManager.Catalog catalog
    ) {
        assertSame(catalog.definitions(),
                ArtisanRecipeDataManager.snapshot());
        Set<ResourceLocation> indexedIds =
                catalog.recipesByMachine().values().stream()
                        .flatMap(java.util.Collection::stream)
                        .map(ArtisanRecipeDataManager.Recipe::id)
                        .collect(java.util.stream.Collectors.toSet());
        assertEquals(
                catalog.definitions().definitions().keySet(),
                indexedIds);
        assertEquals(
                catalog.recipesByMachine().keySet(),
                JsonParser.parseString(catalog.cachedJson())
                        .getAsJsonObject()
                        .keySet());
    }
}
