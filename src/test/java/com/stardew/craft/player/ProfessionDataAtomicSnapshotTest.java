package com.stardew.craft.player;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfessionDataAtomicSnapshotTest {
    @Test
    void definitionsLookupsAndSyncJsonPublishTogether() {
        String previous = ProfessionData.getCachedJson();
        try {
            ProfessionData.applyFromJson("""
                    {
                      "stardewcraft:tiller": {
                        "skill": "stardewcraft:farming",
                        "required_level": 5,
                        "name_key": "test.tiller.name",
                        "desc_key": "test.tiller.desc"
                      }
                    }
                    """);

            ProfessionData.Catalog accepted =
                    ProfessionData.catalog();
            assertCoherent(accepted);
            assertTrue(ProfessionData
                    .definition(ProfessionType.TILLER)
                    .isPresent());

            ProfessionData.applyFromJson("""
                    {
                      "stardewcraft:tiller": {
                        "skill": "stardewcraft:farming",
                        "required_level": 11,
                        "name_key": "test.invalid.name",
                        "desc_key": "test.invalid.desc"
                      }
                    }
                    """);

            assertSame(accepted, ProfessionData.catalog(),
                    "invalid profession sync replaced the accepted catalog");
            assertCoherent(accepted);

            ProfessionData.applyFromJson("""
                    {
                      "stardewcraft:rancher": {
                        "skill": "stardewcraft:farming",
                        "required_level": 5,
                        "name_key": "test.rancher.name",
                        "desc_key": "test.rancher.desc"
                      }
                    }
                    """);
            assertCoherent(ProfessionData.catalog());
        } finally {
            ProfessionData.applyFromJson(previous);
        }
    }

    private static void assertCoherent(
            ProfessionData.Catalog catalog
    ) {
        assertSame(catalog.definitions(),
                ProfessionData.snapshot());
        assertEquals(
                catalog.definitions().definitions().keySet(),
                JsonParser.parseString(catalog.cachedJson())
                        .getAsJsonObject()
                        .keySet().stream()
                        .map(ResourceLocation::parse)
                        .collect(java.util.stream.Collectors.toSet()));
    }
}
