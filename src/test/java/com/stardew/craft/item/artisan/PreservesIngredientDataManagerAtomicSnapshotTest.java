package com.stardew.craft.item.artisan;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreservesIngredientDataManagerAtomicSnapshotTest {
    @Test
    void ingredientLookupsAndSyncJsonPublishTogether() {
        String previous = PreservesIngredientDataManager.getCachedJson();
        try {
            PreservesIngredientDataManager.applyFromJson("""
                    {
                      "apple": {
                        "price": 100,
                        "edibility": 38,
                        "color": "#ff0000"
                      }
                    }
                    """);

            PreservesIngredientDataManager.Catalog accepted =
                    PreservesIngredientDataManager.catalog();
            assertCoherent(accepted);
            assertTrue(PreservesIngredientDataManager.hasData(
                    ResourceLocation.fromNamespaceAndPath(
                            "minecraft", "apple")));

            PreservesIngredientDataManager.applyFromJson("{");

            assertSame(accepted,
                    PreservesIngredientDataManager.catalog(),
                    "invalid preserves sync replaced the accepted catalog");
            assertCoherent(accepted);

            PreservesIngredientDataManager.applyFromJson("""
                    {
                      "golden_carrot": {
                        "price": 200,
                        "edibility": 50,
                        "color": "#ffaa00"
                      }
                    }
                    """);
            assertCoherent(PreservesIngredientDataManager.catalog());
        } finally {
            PreservesIngredientDataManager.applyFromJson(
                    previous.isBlank() ? "{}" : previous);
        }
    }

    private static void assertCoherent(
            PreservesIngredientDataManager.Catalog catalog
    ) {
        assertSame(catalog,
                PreservesIngredientDataManager.catalog());
        assertEquals(
                catalog.data().keySet(),
                JsonParser.parseString(
                                PreservesIngredientDataManager
                                        .getCachedJson())
                        .getAsJsonObject()
                        .keySet());
    }
}
