package com.stardew.craft.animal.model;

/**
 * One publication point for the mutually dependent animal and building registries.
 *
 * <p>Readers either observe both old snapshots or both new snapshots. This avoids the reload
 * window where a newly published animal could require a building tier which had not been
 * published yet (or vice versa).
 */
final class AnimalDefinitionSnapshot {
    private static volatile Published published;

    private AnimalDefinitionSnapshot() {
    }

    static FarmAnimalDefinitions.Snapshot animals(
            FarmAnimalDefinitions.Snapshot fallback
    ) {
        Published current = published;
        return current == null ? fallback : current.animals();
    }

    static AnimalBuildingTierDefinitions.Snapshot buildings(
            AnimalBuildingTierDefinitions.Snapshot fallback
    ) {
        Published current = published;
        return current == null ? fallback : current.buildings();
    }

    static void publish(
            FarmAnimalDefinitions.Snapshot animals,
            AnimalBuildingTierDefinitions.Snapshot buildings
    ) {
        published = new Published(animals, buildings);
    }

    static void validateCrossReferences(
            FarmAnimalDefinitions.Snapshot animals,
            AnimalBuildingTierDefinitions.Snapshot buildings
    ) {
        for (FarmAnimalDefinition animal
                : animals.orderedDefinitions()) {
            int requiredTier =
                    animal.requiredBuildingTier();
            if (requiredTier <= 0) {
                continue;
            }
            String key = animal.family()
                    + ":" + requiredTier;
            if (!buildings.byKey().containsKey(key)) {
                throw new IllegalArgumentException(
                        animal.dataId()
                                + " requires missing animal-building tier "
                                + key);
            }
        }
    }

    private record Published(
            FarmAnimalDefinitions.Snapshot animals,
            AnimalBuildingTierDefinitions.Snapshot buildings
    ) {
    }
}
