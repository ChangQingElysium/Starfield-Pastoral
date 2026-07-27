package com.stardew.craft.shop;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class MonsterSlayerGoalRegistryAtomicSnapshotTest {
    @Test
    void goalsAndTagRoutingPublishTogether() {
        Map<String, MonsterSlayerGoalRegistry.SlayerGoal> previous =
                MonsterSlayerGoalRegistry.catalog().goals();
        try {
            MonsterSlayerGoalRegistry.SlayerGoal first =
                    new MonsterSlayerGoalRegistry.SlayerGoal(
                            "addon:first",
                            "test.first",
                            10,
                            List.of("slime", "cave"),
                            List.of());
            MonsterSlayerGoalRegistry.applyCandidate(
                    Map.of(first.goalKey(), first), List.of());

            MonsterSlayerGoalRegistry.Catalog accepted =
                    MonsterSlayerGoalRegistry.catalog();
            assertCoherent(accepted);
            assertEquals(first.goalKey(),
                    MonsterSlayerGoalRegistry
                            .getGoalKeyForTag("slime"));

            MonsterSlayerGoalRegistry.applyCandidate(
                    Map.of(), List.of("invalid test candidate"));

            assertSame(accepted,
                    MonsterSlayerGoalRegistry.catalog(),
                    "invalid slayer goals replaced the accepted catalog");
            assertCoherent(accepted);

            MonsterSlayerGoalRegistry.SlayerGoal second =
                    new MonsterSlayerGoalRegistry.SlayerGoal(
                            "addon:second",
                            "test.second",
                            20,
                            List.of("bat"),
                            List.of());
            MonsterSlayerGoalRegistry.applyCandidate(
                    Map.of(second.goalKey(), second), List.of());
            assertCoherent(MonsterSlayerGoalRegistry.catalog());
        } finally {
            MonsterSlayerGoalRegistry.applyCandidate(
                    previous, List.of());
        }
    }

    private static void assertCoherent(
            MonsterSlayerGoalRegistry.Catalog catalog
    ) {
        assertSame(catalog,
                MonsterSlayerGoalRegistry.catalog());
        catalog.tagToGoals().forEach((tag, ids) ->
                assertEquals(ids,
                        MonsterSlayerGoalRegistry
                                .getGoalKeysForTag(tag)));
        assertEquals(catalog.goals().values().stream().toList(),
                MonsterSlayerGoalRegistry.getAllGoals());
    }
}
