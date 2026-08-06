package com.stardew.craft.menu;

import com.stardew.craft.animal.model.AnimalBuildingRecord;

/** Resolves whether an animal-building manager should build, upgrade or revalidate. */
final class AnimalBuildingMenuFlow {
    private AnimalBuildingMenuFlow() {
    }

    static Decision resolve(
            int currentTier,
            AnimalBuildingRecord.ValidationState validationState
    ) {
        int tier = Math.max(0, currentTier);
        if (tier > 0
                && validationState
                == AnimalBuildingRecord.ValidationState.INVALID) {
            return new Decision(tier, true, true);
        }
        if (validationState
                == AnimalBuildingRecord.ValidationState.RELOCATING) {
            return new Decision(0, false, false);
        }
        if (tier >= 3) {
            return new Decision(0, false, false);
        }
        return new Decision(tier + 1, true, false);
    }

    record Decision(
            int targetTier,
            boolean shouldScan,
            boolean revalidating
    ) {
    }
}
