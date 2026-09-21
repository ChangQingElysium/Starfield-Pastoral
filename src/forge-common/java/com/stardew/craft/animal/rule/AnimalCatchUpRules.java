package com.stardew.craft.animal.rule;

import java.util.Optional;

/** Pure ordering rules for resumable animal-day catch-up. */
public final class AnimalCatchUpRules {
    private AnimalCatchUpRules() {
    }

    /**
     * Old saves without a checkpoint start at yesterday, preserving the legacy "settle once"
     * migration behavior instead of inventing an unbounded history.
     */
    public static int initializeCheckpoint(int lastProcessedAbsDay, int currentAbsDay) {
        return lastProcessedAbsDay <= 0
                ? Math.max(0, currentAbsDay - 1)
                : lastProcessedAbsDay;
    }

    public static Optional<Step> nextStep(int lastProcessedAbsDay, int currentAbsDay) {
        int checkpoint = initializeCheckpoint(lastProcessedAbsDay, currentAbsDay);
        if (checkpoint >= currentAbsDay) {
            return Optional.empty();
        }
        int targetDay = checkpoint + 1;
        return Optional.of(new Step(targetDay, targetDay < currentAbsDay));
    }

    /**
     * A normal overnight pass must visit every due animal once before the new day is exposed.
     * The fixed minimum remains useful for catch-up, but must not truncate a large active farm.
     */
    public static int initialSettlementBudget(int minimumBudget, int dueAnimalCount) {
        return Math.max(Math.max(0, minimumBudget), Math.max(0, dueAnimalCount));
    }

    public record Step(int targetAbsDay, boolean offlineCatchUp) {
    }
}
