package com.stardew.craft.world.data;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.stardew.craft.api.v1.content.DefinitionDiagnostic;
import com.stardew.craft.api.v1.world.StardewForageZoneDefinition;
import com.stardew.craft.api.v1.world.StardewWorldLootPoolDefinition;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class WorldDataAtomicSnapshotTest {
    @Test
    void worldLootDefinitionsAndPriorityOrderPublishTogether() {
        Map<ResourceLocation, StardewWorldLootPoolDefinition> previous =
                WorldLootPoolData.snapshot().definitions();
        try {
            ResourceLocation lowId = id("loot_low");
            ResourceLocation highId = id("loot_high");
            StardewWorldLootPoolDefinition low = loot(1);
            StardewWorldLootPoolDefinition high = loot(100);
            Map<ResourceLocation, StardewWorldLootPoolDefinition> definitions =
                    Map.of(lowId, low, highId, high);

            WorldLootPoolData.applyCandidate(
                    definitions, sources(definitions), List.of());
            WorldLootPoolData.Catalog accepted =
                    WorldLootPoolData.catalog();
            assertSame(accepted.definitions(),
                    WorldLootPoolData.snapshot());
            assertEquals(List.of(highId, lowId),
                    accepted.ordered().stream()
                            .map(Map.Entry::getKey).toList());

            WorldLootPoolData.applyCandidate(
                    Map.of(), Map.of(),
                    List.of(DefinitionDiagnostic.error(
                            null, null, "invalid test candidate")));
            assertSame(accepted, WorldLootPoolData.catalog());
        } finally {
            WorldLootPoolData.applyCandidate(
                    previous, sources(previous), List.of());
        }
    }

    @Test
    void forageDefinitionsAndPriorityOrderPublishTogether() {
        Map<ResourceLocation, StardewForageZoneDefinition> previous =
                ForageZoneData.snapshot().definitions();
        try {
            ResourceLocation lowId = id("forage_low");
            ResourceLocation highId = id("forage_high");
            StardewForageZoneDefinition low = forage(1);
            StardewForageZoneDefinition high = forage(100);
            Map<ResourceLocation, StardewForageZoneDefinition> definitions =
                    Map.of(lowId, low, highId, high);

            ForageZoneData.applyCandidate(
                    definitions, sources(definitions), List.of());
            ForageZoneData.Catalog accepted =
                    ForageZoneData.catalog();
            assertSame(accepted.definitions(),
                    ForageZoneData.snapshot());
            assertEquals(List.of(highId, lowId),
                    accepted.ordered().stream()
                            .map(Map.Entry::getKey).toList());

            ForageZoneData.applyCandidate(
                    Map.of(), Map.of(),
                    List.of(DefinitionDiagnostic.error(
                            null, null, "invalid test candidate")));
            assertSame(accepted, ForageZoneData.catalog());
        } finally {
            ForageZoneData.applyCandidate(
                    previous, sources(previous), List.of());
        }
    }

    private static StardewWorldLootPoolDefinition loot(int priority) {
        return StardewWorldLootPoolDefinition.CODEC.parse(
                        JsonOps.INSTANCE,
                        JsonParser.parseString("""
                                {
                                  "source": "stardewcraft:artifact_spot",
                                  "group": "test",
                                  "priority": %d,
                                  "mode": "weighted",
                                  "entries": [{
                                    "query": {
                                      "type": "stardewcraft:item",
                                      "data": {"item": "minecraft:apple"}
                                    }
                                  }]
                                }
                                """.formatted(priority)))
                .getOrThrow();
    }

    private static StardewForageZoneDefinition forage(int priority) {
        return StardewForageZoneDefinition.CODEC.parse(
                        JsonOps.INSTANCE,
                        JsonParser.parseString("""
                                {
                                  "priority": %d,
                                  "areas": [{
                                    "min_x": 0,
                                    "min_y": 60,
                                    "min_z": 0,
                                    "max_x": 1,
                                    "max_y": 70,
                                    "max_z": 1
                                  }],
                                  "min_daily_spawn": 1,
                                  "max_daily_spawn": 1,
                                  "max_spawned_at_once": 1,
                                  "surface": "natural",
                                  "entries": [{
                                    "block": "minecraft:dandelion",
                                    "seasons": ["spring"],
                                    "chance": 1.0
                                  }]
                                }
                                """.formatted(priority)))
                .getOrThrow();
    }

    private static <T> Map<ResourceLocation, String> sources(
            Map<ResourceLocation, T> definitions
    ) {
        return definitions.keySet().stream().collect(
                java.util.stream.Collectors.toMap(
                        id -> id, id -> "{}"));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                "world_snapshot_test", path);
    }
}
