package com.stardew.craft.api.v1.progress;

import com.stardew.craft.api.v1.festival.StardewFestivalActivities;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StardewProgressTypesTest {
    @Test
    void snapshotDefensivelyCopiesMetricsAndPreservesNamespacedIdentity() {
        StardewProgressKey key = new StardewProgressKey(
                ResourceLocation.parse("example:orchard_trials"),
                ResourceLocation.parse("example:first_harvest"));
        ArrayList<StardewProgressMetric> source = new ArrayList<>(List.of(
                new StardewProgressMetric(
                        ResourceLocation.parse("example:apples"), 2, 5)));

        StardewProgressSnapshot snapshot = new StardewProgressSnapshot(
                key,
                StardewProgressScope.PLAYER,
                StardewProgressPhase.ACTIVE,
                source,
                true,
                false,
                OptionalInt.of(3));
        source.clear();

        assertEquals(key, snapshot.key());
        assertEquals(1, snapshot.metrics().size());
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.metrics().clear());
        assertEquals(3, snapshot.remainingDays().orElseThrow());
    }

    @Test
    void invalidMetricsAndClaimablePhasesAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new StardewProgressMetric(
                        ResourceLocation.parse("example:apples"), 6, 5));
        assertThrows(IllegalArgumentException.class,
                () -> new StardewProgressSnapshot(
                        new StardewProgressKey(
                                StardewProgressDomains.QUEST,
                                ResourceLocation.parse("example:quest")),
                        StardewProgressScope.PLAYER,
                        StardewProgressPhase.ACTIVE,
                        List.of(),
                        true,
                        true,
                        OptionalInt.empty()));
        assertTrue(StardewProgressPhase.COMPLETED.terminal());
        assertTrue(StardewProgressPhase.CANCELLED.terminal());
    }

    @Test
    void builtInCollectionHelpersKeepLegacyAndNamespacedIdsStable() {
        assertEquals(
                ResourceLocation.parse("stardewcraft:bundle/7"),
                StardewProgress.communityCenterBundle(7).id());
        assertEquals(
                ResourceLocation.parse("stardewcraft:area/2"),
                StardewProgress.communityCenterArea(2).id());
        assertEquals(
                ResourceLocation.parse("stardewcraft:collection"),
                StardewProgress.museumCollection().id());
        assertEquals(
                ResourceLocation.parse(
                        "stardewcraft:reward/ancient_seed_reward"),
                StardewProgress.museumReward(
                        "ancient_seed_reward").id());
        assertEquals(
                ResourceLocation.parse("example:reward/deep_minerals"),
                StardewProgress.museumReward(
                        "example:deep_minerals").id());
        assertEquals(
                new StardewProgressKey(
                        StardewProgressDomains.FESTIVAL,
                        ResourceLocation.parse("example:apple_day")),
                StardewProgress.festival(
                        ResourceLocation.parse("example:apple_day")));
        assertEquals(
                new StardewProgressKey(
                        ResourceLocation.parse(
                                "example:festival_activity/apple_day"),
                        ResourceLocation.parse("other:apple_toss")),
                StardewFestivalActivities.progressKey(
                        ResourceLocation.parse("example:apple_day"),
                        ResourceLocation.parse("other:apple_toss")));
        assertThrows(
                IllegalArgumentException.class,
                () -> StardewProgress.communityCenterBundle(-1));
    }
}
