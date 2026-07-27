package com.stardew.craft.mining;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.stardew.craft.api.v1.content.DefinitionDiagnostic;
import com.stardew.craft.api.v1.mining.StardewMineMonsterContext;
import com.stardew.craft.api.v1.mining.StardewMineMonsterSpawnEntry;
import com.stardew.craft.api.v1.mining.StardewMineMonsterSpawnTableDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MineMonsterSpawnTableDataTest {
    @Test
    void exactThemeTableSelectsProfile() {
        ResourceLocation profile = id("orchard_slime");
        StardewMineMonsterSpawnTableDefinition table = table(
                List.of(id("orchard_floor")),
                List.of(),
                Optional.of(false),
                4.0,
                20.0,
                profile);

        ResourceLocation selected =
                MineMonsterSpawnTableData.selectProfileId(
                        table,
                        context(13, false, 8.0),
                        id("orchard_floor"),
                        "earth");

        assertEquals(profile, selected);
    }

    @Test
    void filtersDoNotConsumeNonMatchingTables() {
        StardewMineMonsterSpawnTableDefinition table = table(
                List.of(),
                List.of("frost"),
                Optional.of(true),
                6.0,
                20.0,
                id("frost_bat"));

        assertNull(MineMonsterSpawnTableData.selectProfileId(
                table,
                context(50, false, 10.0),
                id("frost"),
                "frost"));
        assertNull(MineMonsterSpawnTableData.selectProfileId(
                table,
                context(50, true, 3.0),
                id("frost"),
                "frost"));
        assertNull(MineMonsterSpawnTableData.selectProfileId(
                table,
                context(50, true, 10.0),
                id("earth"),
                "earth"));
    }

    @Test
    void codecRejectsEmptyEntriesAndReversedRanges() {
        var emptyEntries = StardewMineMonsterSpawnTableDefinition.CODEC
                .parse(JsonOps.INSTANCE, JsonParser.parseString("""
                        {
                          "min_floor": 1,
                          "max_floor": 10,
                          "entries": []
                        }
                        """));
        var reversedRange = StardewMineMonsterSpawnTableDefinition.CODEC
                .parse(JsonOps.INSTANCE, JsonParser.parseString("""
                        {
                          "min_floor": 20,
                          "max_floor": 10,
                          "entries": [
                            {"profile": "stardewcraft:green_slime"}
                          ]
                        }
                        """));

        assertTrue(emptyEntries.error().isPresent());
        assertTrue(reversedRange.error().isPresent());
    }

    @Test
    void definitionsAndPriorityOrderPublishTogether() {
        Map<ResourceLocation, StardewMineMonsterSpawnTableDefinition>
                previous =
                MineMonsterSpawnTableData.snapshot().definitions();
        try {
            ResourceLocation lowId = id("table_low");
            ResourceLocation highId = id("table_high");
            StardewMineMonsterSpawnTableDefinition low =
                    table(List.of(), List.of(), Optional.empty(),
                            0, 20, id("profile_low"));
            StardewMineMonsterSpawnTableDefinition high =
                    new StardewMineMonsterSpawnTableDefinition(
                            1, 120, 100,
                            List.of(), List.of(), Optional.empty(),
                            0, 20,
                            List.of(new StardewMineMonsterSpawnEntry(
                                    id("profile_high"), 1)));
            Map<ResourceLocation,
                    StardewMineMonsterSpawnTableDefinition> definitions =
                    Map.of(lowId, low, highId, high);

            MineMonsterSpawnTableData.applyCandidate(
                    definitions, sources(definitions), List.of());
            MineMonsterSpawnTableData.Catalog accepted =
                    MineMonsterSpawnTableData.catalog();
            assertSame(accepted.definitions(),
                    MineMonsterSpawnTableData.snapshot());
            assertEquals(List.of(highId, lowId),
                    accepted.ordered().stream()
                            .map(Map.Entry::getKey).toList());

            MineMonsterSpawnTableData.applyCandidate(
                    Map.of(), Map.of(),
                    List.of(DefinitionDiagnostic.error(
                            null, null, "invalid test candidate")));
            assertSame(accepted,
                    MineMonsterSpawnTableData.catalog());
        } finally {
            MineMonsterSpawnTableData.applyCandidate(
                    previous, sources(previous), List.of());
        }
    }

    private static <T> Map<ResourceLocation, String> sources(
            Map<ResourceLocation, T> definitions
    ) {
        return definitions.keySet().stream().collect(
                java.util.stream.Collectors.toMap(
                        id -> id, id -> "{}"));
    }

    private static StardewMineMonsterSpawnTableDefinition table(
            List<ResourceLocation> themes,
            List<String> mechanics,
            Optional<Boolean> dark,
            double minDistance,
            double maxDistance,
            ResourceLocation profile
    ) {
        return new StardewMineMonsterSpawnTableDefinition(
                1,
                120,
                0,
                themes,
                mechanics,
                dark,
                minDistance,
                maxDistance,
                List.of(new StardewMineMonsterSpawnEntry(profile, 1)));
    }

    private static StardewMineMonsterContext context(
            int floor,
            boolean dark,
            double distance
    ) {
        return new StardewMineMonsterContext(
                floor, dark, distance, RandomSource.create(42L));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                "stardewcraft_test", path);
    }
}
