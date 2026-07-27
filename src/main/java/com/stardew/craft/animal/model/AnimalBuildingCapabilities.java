package com.stardew.craft.animal.model;

/** Immutable gameplay capabilities resolved from a validated building snapshot. */
public record AnimalBuildingCapabilities(
        String family,
        int tier,
        int animalCapacity,
        int hayCapacity,
        boolean allowsPregnancy,
        boolean automaticFeed
) {
    public static AnimalBuildingCapabilities from(AnimalBuildingRecord building) {
        AnimalBuildingType type = building.buildingType();
        return new AnimalBuildingCapabilities(
                type.family(),
                type.tier(),
                building.capacity(),
                building.hayCapacity(),
                type.allowsAnimalPregnancy(),
                type.hasAutomaticFeed()
        );
    }
}
