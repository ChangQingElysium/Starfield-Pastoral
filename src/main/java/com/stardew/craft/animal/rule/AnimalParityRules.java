package com.stardew.craft.animal.rule;

import com.stardew.craft.animal.model.FarmAnimalDefinition;
import com.stardew.craft.item.quality.QualityHelper;

import java.util.Objects;
import java.util.function.DoubleSupplier;

/**
 * Pure, world-independent ports of small {@code FarmAnimal.cs} rules.
 *
 * <p>Keeping these calculations outside entities and managers makes RNG order and edge cases
 * directly testable.
 */
public final class AnimalParityRules {
    private static final int MANUAL_PET_FRIENDSHIP = 15;
    private static final int AUTO_PET_REDUCTION = 7;

    private AnimalParityRules() {
    }

    /** SDV uses integer division here: {@code 10 - friendship / 200}. */
    public static int missedPetFriendshipPenalty(int friendship) {
        int clamped = Math.max(0, Math.min(1000, friendship));
        return Math.max(0, 10 - clamped / 200);
    }

    public static int productionInterval(
            FarmAnimalDefinition definition,
            int friendship,
            boolean hasFasterProduceProfession
    ) {
        Objects.requireNonNull(definition, "definition");
        int speedBonus = definition.friendshipForFasterProduce() >= 0
                && friendship >= definition.friendshipForFasterProduce()
                ? 1
                : 0;
        if (definition.professionForFasterProduce() >= 0 && hasFasterProduceProfession) {
            speedBonus++;
        }
        return definition.daysToProduce() - speedBonus;
    }

    /**
     * Port of the three independent quality rolls in {@code FarmAnimal.dayUpdate}.
     */
    public static int rollProduceQuality(
            int friendship,
            int happiness,
            boolean hasQualityProfession,
            DoubleSupplier random
    ) {
        Objects.requireNonNull(random, "random");
        double chance = (float) friendship / 1000.0F - (1.0F - (float) happiness / 225.0F);
        if (hasQualityProfession) {
            chance += 0.33D;
        }
        if (chance >= 0.95D && random.getAsDouble() < chance / 2.0D) {
            return QualityHelper.IRIDIUM;
        }
        if (random.getAsDouble() < chance / 2.0D) {
            return QualityHelper.GOLD;
        }
        if (random.getAsDouble() < chance) {
            return QualityHelper.SILVER;
        }
        return QualityHelper.NORMAL;
    }

    /** In SDV this probability keeps the pig's current produce so it can dig again. */
    public static boolean keepsPigProduceAfterDig(int friendship, double roll) {
        int clamped = Math.max(0, Math.min(1000, friendship));
        return roll < clamped / 1500.0D;
    }

    /** Source {@code FarmAnimal.DigUpProduce} Truffle Crab replacement roll. */
    public static boolean spawnsTruffleCrab(boolean isTruffle, double roll) {
        return isTruffle && roll < 0.002D;
    }

    /** Source gate from {@code FarmAnimal.updateWhenCurrentLocation}. */
    public static boolean canLeaveHome(
            int timeOfDay,
            boolean raining,
            boolean winter,
            boolean farmerInside
    ) {
        return timeOfDay < 16 * 60 + 30
                && !raining
                && !winter
                && !farmerInside;
    }

    /** Source interaction text is shown from 19:00 when movement has stopped. */
    public static boolean isTryingToSleep(
            int timeOfDay,
            boolean moving
    ) {
        return timeOfDay >= 19 * 60 && !moving;
    }

    /** Source sleep state begins at 20:00 even if the animal is still outdoors. */
    public static boolean shouldSleep(int timeOfDay) {
        return timeOfDay >= 20 * 60;
    }

    /** Source {@code CanFollowAdult}: only baby types which opt in through animal data follow. */
    public static boolean canFollowAdult(
            FarmAnimalDefinition definition,
            boolean baby
    ) {
        return definition != null
                && baby
                && definition.babiesFollowAdults();
    }

    /**
     * Source Auto-Grabber checks held tool produce independently of whether
     * the animal produced during the current daily update.
     */
    public static boolean shouldAutoCollectHeldProduce(
            FarmAnimalDefinition definition,
            boolean hasAutoGrabber,
            String currentProduceId
    ) {
        return definition != null
                && definition.harvestType()
                == FarmAnimalDefinition.HarvestType.HARVEST_WITH_TOOL
                && hasAutoGrabber
                && currentProduceId != null
                && !currentProduceId.isBlank();
    }

    public static PetOutcome pet(
            boolean wasPetToday,
            boolean wasAutoPetToday,
            boolean automatic,
            int happinessDrain,
            boolean hasHappinessProfession
    ) {
        if (wasPetToday || (automatic && wasAutoPetToday)) {
            return PetOutcome.notApplied(wasPetToday, wasAutoPetToday);
        }

        int friendshipDelta;
        if (wasAutoPetToday) {
            friendshipDelta = AUTO_PET_REDUCTION;
        } else if (automatic) {
            friendshipDelta = MANUAL_PET_FRIENDSHIP - AUTO_PET_REDUCTION;
        } else {
            friendshipDelta = MANUAL_PET_FRIENDSHIP;
        }

        int happinessGain = Math.max(5, 30 + happinessDrain);
        if (!automatic && hasHappinessProfession) {
            friendshipDelta += MANUAL_PET_FRIENDSHIP;
            happinessGain *= 2;
        }

        return new PetOutcome(
                true,
                friendshipDelta,
                happinessGain,
                automatic ? 0 : 5,
                wasPetToday || !automatic,
                wasAutoPetToday || automatic
        );
    }

    public record PetOutcome(
            boolean applied,
            int friendshipDelta,
            int happinessDelta,
            int farmingExperience,
            boolean wasPetToday,
            boolean wasAutoPetToday
    ) {
        private static PetOutcome notApplied(boolean wasPetToday, boolean wasAutoPetToday) {
            return new PetOutcome(false, 0, 0, 0, wasPetToday, wasAutoPetToday);
        }
    }
}
