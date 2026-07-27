package com.stardew.craft.fishing.data;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class FishingDataManagerAtomicSnapshotTest {
    @Test
    void locationRulesAndSyncJsonBelongToOneManagerInstance() {
        String previous = FishingDataManager.getCachedJson();
        try {
            FishingDataManager.applyFromJson("""
                    {
                      "FishingSnapshotTest": {
                        "location": "FishingSnapshotTest",
                        "fish": [{
                          "id": "first",
                          "item": "minecraft:cod"
                        }]
                      }
                    }
                    """);

            FishingDataManager accepted =
                    FishingDataManager.get();
            assertCoherent(accepted);
            assertEquals(1,
                    accepted.getLocationDataSnapshot()
                            .get("FishingSnapshotTest")
                            .fish().size());

            FishingDataManager.applyFromJson("""
                    {
                      "Broken": 42
                    }
                    """);

            assertSame(accepted, FishingDataManager.get(),
                    "an invalid fishing sync replaced the accepted manager");
            assertCoherent(accepted);

            FishingDataManager.applyFromJson("""
                    {
                      "FishingSnapshotReplacement": {
                        "location": "FishingSnapshotReplacement",
                        "fish": [{
                          "id": "second",
                          "item": "minecraft:salmon"
                        }]
                      }
                    }
                    """);

            assertCoherent(FishingDataManager.get());
            assertEquals(1,
                    FishingDataManager.get()
                            .getLocationDataSnapshot()
                            .get("FishingSnapshotReplacement")
                            .fish().size());
        } finally {
            FishingDataManager.applyFromJson(
                    previous.isBlank() ? "{}" : previous);
        }
    }

    private static void assertCoherent(
            FishingDataManager manager
    ) {
        assertSame(manager, FishingDataManager.get());
        assertEquals(
                manager.getLocationDataSnapshot().keySet(),
                JsonParser.parseString(
                                FishingDataManager.getCachedJson())
                        .getAsJsonObject()
                        .keySet());
    }
}
