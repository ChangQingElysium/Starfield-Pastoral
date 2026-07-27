package com.stardew.craft.player;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnlockSourceDataAtomicSnapshotTest {
    @Test
    void definitionsLookupReverseIndexAndSyncJsonPublishTogether() {
        String previous = UnlockSourceData.getCachedJson();
        ResourceLocation firstId = ResourceLocation.fromNamespaceAndPath(
                "unlock_snapshot_test", "first");
        try {
            UnlockSourceData.applyFromJson("""
                    {
                      "unlock_snapshot_test:first": {
                        "recipes": [
                          "unlock_snapshot_test:apple_press"
                        ],
                        "wallpapers": [],
                        "floorings": []
                      }
                    }
                    """);

            UnlockSourceData.Catalog accepted =
                    UnlockSourceData.catalog();
            assertCoherent(accepted);
            assertTrue(UnlockSourceData.hasSource(
                    firstId.toString()));
            assertEquals(
                    List.of(firstId),
                    UnlockSourceData.getSourceIdsForRecipe(
                            "unlock_snapshot_test:apple_press"));

            UnlockSourceData.applyFromJson("""
                    {
                      "invalid source id": {
                        "recipes": []
                      }
                    }
                    """);

            assertSame(accepted, UnlockSourceData.catalog(),
                    "an invalid source ID replaced the accepted catalog");
            assertTrue(UnlockSourceData.hasSource(
                    firstId.toString()));

            UnlockSourceData.applyFromJson("""
                    {
                      "unlock_snapshot_test:second": {
                        "recipes": [
                          "unlock_snapshot_test:smoker"
                        ]
                      }
                    }
                    """);

            UnlockSourceData.Catalog replacement =
                    UnlockSourceData.catalog();
            assertCoherent(replacement);
            assertTrue(replacement.definitions().version()
                    > accepted.definitions().version());
        } finally {
            UnlockSourceData.applyFromJson(previous);
        }
    }

    private static void assertCoherent(
            UnlockSourceData.Catalog catalog
    ) {
        assertSame(catalog.definitions(),
                UnlockSourceData.snapshot());
        Set<ResourceLocation> sourceIds =
                catalog.sources().keySet();
        assertEquals(
                sourceIds,
                UnlockSourceData.getSourceIds().stream()
                        .map(ResourceLocation::parse)
                        .collect(java.util.stream.Collectors.toSet()));
        assertEquals(
                sourceIds,
                JsonParser.parseString(catalog.cachedJson())
                        .getAsJsonObject()
                        .keySet().stream()
                        .map(ResourceLocation::parse)
                        .collect(java.util.stream.Collectors.toSet()));
    }
}
