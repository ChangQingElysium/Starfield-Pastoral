package com.stardew.craft.mining;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.stardew.craft.api.v1.content.DefinitionDiagnostic;
import com.stardew.craft.api.v1.loot.StardewMineChestRewardDefinition;
import com.stardew.craft.api.v1.mining.StardewMineThemeDefinition;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class MiningDataAtomicSnapshotTest {
    @Test
    void themeDefinitionsAndPriorityOrderPublishTogether() {
        Map<ResourceLocation, StardewMineThemeDefinition> previous =
                MineThemeData.snapshot().definitions();
        try {
            ResourceLocation lowId = id("theme_low");
            ResourceLocation highId = id("theme_high");
            StardewMineThemeDefinition low = theme(1);
            StardewMineThemeDefinition high = theme(100);
            Map<ResourceLocation, StardewMineThemeDefinition> definitions =
                    Map.of(lowId, low, highId, high);

            MineThemeData.applyCandidate(
                    definitions, sources(definitions), List.of());
            MineThemeData.Catalog accepted =
                    MineThemeData.catalog();
            assertSame(accepted.definitions(),
                    MineThemeData.snapshot());
            assertEquals(List.of(highId, lowId),
                    accepted.ordered().stream()
                            .map(Map.Entry::getKey).toList());

            MineThemeData.applyCandidate(
                    Map.of(), Map.of(),
                    List.of(DefinitionDiagnostic.error(
                            null, null, "invalid test candidate")));
            assertSame(accepted, MineThemeData.catalog());
        } finally {
            MineThemeData.applyCandidate(
                    previous, sources(previous), List.of());
        }
    }

    @Test
    void chestDefinitionsAndPriorityOrderPublishTogether() {
        Map<ResourceLocation, StardewMineChestRewardDefinition> previous =
                MineChestRewardData.snapshot().definitions();
        try {
            ResourceLocation lowId = id("chest_low");
            ResourceLocation highId = id("chest_high");
            StardewMineChestRewardDefinition low = chest(1);
            StardewMineChestRewardDefinition high = chest(100);
            Map<ResourceLocation, StardewMineChestRewardDefinition> definitions =
                    Map.of(lowId, low, highId, high);

            MineChestRewardData.applyCandidate(
                    definitions, sources(definitions), List.of());
            MineChestRewardData.Catalog accepted =
                    MineChestRewardData.catalog();
            assertSame(accepted.definitions(),
                    MineChestRewardData.snapshot());
            assertEquals(List.of(highId, lowId),
                    accepted.ordered().stream()
                            .map(Map.Entry::getKey).toList());

            MineChestRewardData.applyCandidate(
                    Map.of(), Map.of(),
                    List.of(DefinitionDiagnostic.error(
                            null, null, "invalid test candidate")));
            assertSame(accepted,
                    MineChestRewardData.catalog());
        } finally {
            MineChestRewardData.applyCandidate(
                    previous, sources(previous), List.of());
        }
    }

    private static StardewMineThemeDefinition theme(int priority) {
        return StardewMineThemeDefinition.CODEC.parse(
                        JsonOps.INSTANCE,
                        JsonParser.parseString("""
                                {
                                  "mechanic_id": "earth",
                                  "min_floor": 1,
                                  "max_floor": 120,
                                  "priority": %d,
                                  "main_stone": "minecraft:stone",
                                  "dark_stone": "minecraft:deepslate",
                                  "decor_a": [],
                                  "decor_b": [],
                                  "decorative_stones": [],
                                  "vanilla_accents": [],
                                  "cave_decorations": [],
                                  "ores": {
                                    "copper": "minecraft:copper_ore",
                                    "iron": "minecraft:iron_ore",
                                    "gold": "minecraft:gold_ore",
                                    "iridium": "minecraft:diamond_ore",
                                    "coal": "minecraft:coal_ore"
                                  }
                                }
                                """.formatted(priority)))
                .getOrThrow();
    }

    private static StardewMineChestRewardDefinition chest(
            int priority
    ) {
        return StardewMineChestRewardDefinition.CODEC.parse(
                        JsonOps.INSTANCE,
                        JsonParser.parseString("""
                                {
                                  "floor": 30,
                                  "priority": %d,
                                  "reward": {
                                    "type": "stardewcraft:item",
                                    "data": {"item": "minecraft:apple"}
                                  }
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
                "mining_snapshot_test", path);
    }
}
