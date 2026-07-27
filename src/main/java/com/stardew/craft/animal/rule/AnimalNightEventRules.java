package com.stardew.craft.animal.rule;

/** Pure source rules from {@code SoundInTheNightEvent(2).setUp}. */
public final class AnimalNightEventRules {
    private AnimalNightEventRules() {
    }

    public static boolean buildingCanBeAttacked(
            boolean animalDoorOpen,
            int outsideAnimalCount
    ) {
        return !animalDoorOpen && outsideAnimalCount > 0;
    }

    public static boolean selectsBuilding(double roll, int farmBuildingCount) {
        return farmBuildingCount > 0
                && roll < 1.0D / farmBuildingCount;
    }
}
