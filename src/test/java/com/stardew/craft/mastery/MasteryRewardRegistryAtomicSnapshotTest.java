package com.stardew.craft.mastery;

import com.google.gson.JsonParser;
import com.stardew.craft.player.SkillType;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MasteryRewardRegistryAtomicSnapshotTest {
    @Test
    void definitionsRuntimeRowsAndSyncJsonPublishTogether() {
        String previous = MasteryRewardRegistry.getCachedJson();
        try {
            MasteryRewardRegistry.applyFromJson("""
                    {
                      "mastery_snapshot_test:farming": {
                        "skill": "stardewcraft:farming",
                        "entries": [{
                          "kind": "placeholder",
                          "name_key": "test.name",
                          "desc_key": "test.desc"
                        }]
                      }
                    }
                    """);

            MasteryRewardRegistry.Catalog accepted =
                    MasteryRewardRegistry.catalog();
            assertCoherent(accepted);
            assertEquals(1,
                    MasteryRewardRegistry
                            .rewardsFor(SkillType.FARMING).size());

            MasteryRewardRegistry.applyFromJson("""
                    {
                      "mastery_snapshot_test:invalid": {
                        "skill": "stardewcraft:farming",
                        "entries": [{
                          "kind": "recipe",
                          "name_key": "test.name",
                          "desc_key": "test.desc"
                        }]
                      }
                    }
                    """);

            assertSame(accepted, MasteryRewardRegistry.catalog(),
                    "invalid mastery sync replaced the accepted catalog");
            assertCoherent(accepted);

            MasteryRewardRegistry.applyFromJson("""
                    {
                      "mastery_snapshot_test:mining": {
                        "skill": "stardewcraft:mining",
                        "entries": [{
                          "kind": "placeholder",
                          "name_key": "test.name",
                          "desc_key": "test.desc"
                        }]
                      }
                    }
                    """);
            assertCoherent(MasteryRewardRegistry.catalog());
            assertTrue(MasteryRewardRegistry
                    .rewardsFor(SkillType.MINING)
                    .stream().findFirst().isPresent());
        } finally {
            MasteryRewardRegistry.applyFromJson(previous);
        }
    }

    private static void assertCoherent(
            MasteryRewardRegistry.Catalog catalog
    ) {
        assertSame(catalog.definitions(),
                MasteryRewardRegistry.snapshot());
        assertEquals(
                catalog.definitions().definitions().keySet(),
                JsonParser.parseString(catalog.cachedJson())
                        .getAsJsonObject()
                        .keySet().stream()
                        .map(ResourceLocation::parse)
                        .collect(java.util.stream.Collectors.toSet()));
    }
}
