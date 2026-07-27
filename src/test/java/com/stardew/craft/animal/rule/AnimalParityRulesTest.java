package com.stardew.craft.animal.rule;

import com.stardew.craft.animal.model.FarmAnimalDefinition;
import com.stardew.craft.animal.model.FarmAnimalDefinitions;
import com.stardew.craft.item.quality.QualityHelper;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.DoubleSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimalParityRulesTest {
    @Test
    void missedPetPenaltyUsesSourceIntegerDivision() {
        assertEquals(10, AnimalParityRules.missedPetFriendshipPenalty(0));
        assertEquals(10, AnimalParityRules.missedPetFriendshipPenalty(199));
        assertEquals(9, AnimalParityRules.missedPetFriendshipPenalty(200));
        assertEquals(6, AnimalParityRules.missedPetFriendshipPenalty(999));
        assertEquals(5, AnimalParityRules.missedPetFriendshipPenalty(1000));
    }

    @Test
    void manualAndAutomaticPettingUseSourceDeltas() {
        AnimalParityRules.PetOutcome manual =
                AnimalParityRules.pet(false, false, false, 7, false);
        assertTrue(manual.applied());
        assertEquals(15, manual.friendshipDelta());
        assertEquals(37, manual.happinessDelta());
        assertEquals(5, manual.farmingExperience());
        assertTrue(manual.wasPetToday());
        assertFalse(manual.wasAutoPetToday());

        AnimalParityRules.PetOutcome automatic =
                AnimalParityRules.pet(false, false, true, 7, true);
        assertTrue(automatic.applied());
        assertEquals(8, automatic.friendshipDelta());
        assertEquals(37, automatic.happinessDelta());
        assertEquals(0, automatic.farmingExperience());
        assertFalse(automatic.wasPetToday());
        assertTrue(automatic.wasAutoPetToday());
    }

    @Test
    void manualPetAfterAutoOnlyRestoresTheSevenPointDifference() {
        AnimalParityRules.PetOutcome outcome =
                AnimalParityRules.pet(false, true, false, 5, false);
        assertTrue(outcome.applied());
        assertEquals(7, outcome.friendshipDelta());
        assertEquals(35, outcome.happinessDelta());
        assertTrue(outcome.wasPetToday());
        assertTrue(outcome.wasAutoPetToday());
    }

    @Test
    void automaticPetAfterManualPetNeverAwardsTwice() {
        AnimalParityRules.PetOutcome outcome =
                AnimalParityRules.pet(true, false, true, 5, false);

        assertFalse(outcome.applied());
        assertEquals(0, outcome.friendshipDelta());
        assertEquals(0, outcome.happinessDelta());
        assertTrue(outcome.wasPetToday());
        assertFalse(outcome.wasAutoPetToday());
    }

    @Test
    void happinessProfessionDoublesManualMoodGainButNeverBoostsAutoPetter() {
        AnimalParityRules.PetOutcome manual =
                AnimalParityRules.pet(false, false, false, 5, true);
        assertEquals(30, manual.friendshipDelta());
        assertEquals(70, manual.happinessDelta());

        AnimalParityRules.PetOutcome automatic =
                AnimalParityRules.pet(false, false, true, 5, true);
        assertEquals(8, automatic.friendshipDelta());
        assertEquals(35, automatic.happinessDelta());
    }

    @Test
    void sheepAloneReceivesTheSourceFasterProductionRules() {
        FarmAnimalDefinition sheep = FarmAnimalDefinitions.require("sheep");
        assertEquals(3, AnimalParityRules.productionInterval(sheep, 899, false));
        assertEquals(2, AnimalParityRules.productionInterval(sheep, 900, false));
        assertEquals(2, AnimalParityRules.productionInterval(sheep, 0, true));
        assertEquals(1, AnimalParityRules.productionInterval(sheep, 900, true));

        FarmAnimalDefinition cow = FarmAnimalDefinitions.require("cow");
        assertEquals(1, AnimalParityRules.productionInterval(cow, 1000, true));
    }

    @Test
    void qualityUsesThreeIndependentRollsInSourceOrder() {
        assertEquals(
                QualityHelper.IRIDIUM,
                AnimalParityRules.rollProduceQuality(
                        1000, 255, false, sequence(0.2)));
        assertEquals(
                QualityHelper.GOLD,
                AnimalParityRules.rollProduceQuality(
                        1000, 255, false, sequence(0.9, 0.2)));
        assertEquals(
                QualityHelper.SILVER,
                AnimalParityRules.rollProduceQuality(
                        1000, 255, false, sequence(0.9, 0.9, 0.2)));
        assertEquals(
                QualityHelper.NORMAL,
                AnimalParityRules.rollProduceQuality(
                        500, 200, false, sequence(0.9, 0.9)));
    }

    @Test
    void friendshipChanceKeepsPigProduceInsteadOfConsumingIt() {
        assertTrue(AnimalParityRules.keepsPigProduceAfterDig(1000, 0.5));
        assertFalse(AnimalParityRules.keepsPigProduceAfterDig(1000, 0.8));
        assertFalse(AnimalParityRules.keepsPigProduceAfterDig(0, 0.0));
    }

    @Test
    void truffleCrabUsesTheSourcePointTwoPercentBoundary() {
        assertTrue(AnimalParityRules.spawnsTruffleCrab(true, 0.001999D));
        assertFalse(AnimalParityRules.spawnsTruffleCrab(true, 0.002D));
        assertFalse(AnimalParityRules.spawnsTruffleCrab(false, 0.0D));
    }

    @Test
    void outdoorAndSleepGatesMatchFarmAnimalSourceTimes() {
        assertTrue(AnimalParityRules.canLeaveHome(
                16 * 60 + 29, false, false, false));
        assertFalse(AnimalParityRules.canLeaveHome(
                16 * 60 + 30, false, false, false));
        assertFalse(AnimalParityRules.canLeaveHome(
                12 * 60, true, false, false));
        assertFalse(AnimalParityRules.canLeaveHome(
                12 * 60, false, true, false));
        assertFalse(AnimalParityRules.canLeaveHome(
                12 * 60, false, false, true));

        assertFalse(AnimalParityRules.isTryingToSleep(
                18 * 60 + 59, false));
        assertFalse(AnimalParityRules.isTryingToSleep(
                19 * 60, true));
        assertTrue(AnimalParityRules.isTryingToSleep(
                19 * 60, false));
        assertFalse(AnimalParityRules.shouldSleep(
                19 * 60 + 59));
        assertTrue(AnimalParityRules.shouldSleep(20 * 60));
    }

    @Test
    void babyFollowBehaviorIsControlledByFarmAnimalData() {
        assertTrue(AnimalParityRules.canFollowAdult(
                FarmAnimalDefinitions.require("white_chicken"),
                true));
        assertTrue(AnimalParityRules.canFollowAdult(
                FarmAnimalDefinitions.require("duck"),
                true));

        assertFalse(AnimalParityRules.canFollowAdult(
                FarmAnimalDefinitions.require("rabbit"),
                true));
        assertFalse(AnimalParityRules.canFollowAdult(
                FarmAnimalDefinitions.require("cow"),
                true));
        assertFalse(AnimalParityRules.canFollowAdult(
                FarmAnimalDefinitions.require("ostrich"),
                true));
        assertFalse(AnimalParityRules.canFollowAdult(
                FarmAnimalDefinitions.require("white_chicken"),
                false));
    }

    @Test
    void autoGrabberRetriesAnyExistingHeldToolProduce() {
        FarmAnimalDefinition sheep =
                FarmAnimalDefinitions.require("sheep");
        assertTrue(
                AnimalParityRules
                        .shouldAutoCollectHeldProduce(
                                sheep,
                                true,
                                "stardewcraft:wool"));

        assertFalse(
                AnimalParityRules
                        .shouldAutoCollectHeldProduce(
                                sheep,
                                false,
                                "stardewcraft:wool"));
        assertFalse(
                AnimalParityRules
                        .shouldAutoCollectHeldProduce(
                                sheep,
                                true,
                                ""));
        assertFalse(
                AnimalParityRules
                        .shouldAutoCollectHeldProduce(
                                FarmAnimalDefinitions.require(
                                        "white_chicken"),
                                true,
                                "stardewcraft:egg_white"));
    }

    private static DoubleSupplier sequence(double... values) {
        AtomicInteger index = new AtomicInteger();
        return () -> {
            int current = index.getAndIncrement();
            if (current >= values.length) {
                throw new AssertionError("Random sequence exhausted at index " + current);
            }
            return values[current];
        };
    }
}
