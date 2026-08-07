package com.stardew.craft.animal.service;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.time.StardewTimeManager;

import java.util.function.Supplier;

/** Immediate activation for a structurally validated Coop/Barn. */
public final class AnimalBuildingConstructionService {
    private AnimalBuildingConstructionService() {
    }

    public record StartResult(
            boolean started,
            String buildingId
    ) {
        private static StartResult failed() {
            return new StartResult(false, "");
        }
    }

    public static StartResult start(
            Supplier<String> applyValidatedStructure
    ) {
        String buildingId;
        try {
            buildingId = applyValidatedStructure.get();
        } catch (RuntimeException exception) {
            StardewCraft.LOGGER.error(
                    "[ANIMAL_BUILDING] Structure transaction failed",
                    exception);
            return StartResult.failed();
        }
        if (buildingId == null || buildingId.isBlank()) {
            return StartResult.failed();
        }
        return new StartResult(true, buildingId);
    }

    public static int currentAbsoluteDay() {
        StardewTimeManager time = StardewTimeManager.get();
        return (time.getCurrentYear() - 1) * (28 * 4)
                + time.getCurrentSeason() * 28
                + time.getCurrentDay();
    }
}
