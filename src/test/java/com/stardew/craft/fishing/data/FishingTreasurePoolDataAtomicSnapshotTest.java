package com.stardew.craft.fishing.data;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.stardew.craft.api.v1.content.DefinitionDiagnostic;
import com.stardew.craft.api.v1.fishing.StardewFishingTreasurePoolDefinition;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertSame;

class FishingTreasurePoolDataAtomicSnapshotTest {
    @Test
    void definitionsAndRuntimePoolsPublishTogether() {
        Map<ResourceLocation, StardewFishingTreasurePoolDefinition> previous =
                FishingTreasurePoolData.snapshot().definitions();
        try {
            ResourceLocation id =
                    ResourceLocation.fromNamespaceAndPath(
                            "fishing_snapshot_test", "bonus");
            StardewFishingTreasurePoolDefinition definition =
                    definition();
            Map<ResourceLocation, StardewFishingTreasurePoolDefinition>
                    definitions = Map.of(id, definition);

            FishingTreasurePoolData.applyCandidate(
                    definitions, sources(definitions), List.of());
            FishingTreasurePoolData.Catalog accepted =
                    FishingTreasurePoolData.catalog();
            assertSame(accepted.definitions(),
                    FishingTreasurePoolData.snapshot());
            assertSame(definition,
                    accepted.definitions().definitions().get(id));

            FishingTreasurePoolData.applyCandidate(
                    Map.of(), Map.of(),
                    List.of(DefinitionDiagnostic.error(
                            null, null, "invalid test candidate")));
            assertSame(accepted,
                    FishingTreasurePoolData.catalog());
        } finally {
            FishingTreasurePoolData.applyCandidate(
                    previous, sources(previous), List.of());
        }
    }

    private static StardewFishingTreasurePoolDefinition definition() {
        return StardewFishingTreasurePoolDefinition.CODEC.parse(
                        JsonOps.INSTANCE,
                        JsonParser.parseString("""
                                {
                                  "chest": "any",
                                  "chance": 0.25,
                                  "rolls": 1,
                                  "entries": [{
                                    "query": {
                                      "type": "stardewcraft:item",
                                      "data": {"item": "minecraft:apple"}
                                    },
                                    "weight": 1
                                  }]
                                }
                                """))
                .getOrThrow();
    }

    private static <T> Map<ResourceLocation, String> sources(
            Map<ResourceLocation, T> definitions
    ) {
        return definitions.keySet().stream().collect(
                java.util.stream.Collectors.toMap(
                        id -> id, id -> "{}"));
    }
}
