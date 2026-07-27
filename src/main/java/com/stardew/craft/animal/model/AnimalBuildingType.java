package com.stardew.craft.animal.model;

public enum AnimalBuildingType {
    COOP_TIER_1("coop", 1),
    COOP_TIER_2("coop", 2),
    COOP_TIER_3("coop", 3),
    BARN_TIER_1("barn", 1),
    BARN_TIER_2("barn", 2),
    BARN_TIER_3("barn", 3),
    SILO_TIER_1("silo", 1);

    private final String family;
    private final int tier;

    AnimalBuildingType(String family, int tier) {
        this.family = family;
        this.tier = tier;
    }

    public String family() {
        return family;
    }

    public int tier() {
        return tier;
    }

    public int defaultCapacity() {
        return definition().capacity();
    }

    public int hayCapacity() {
        return definition().hayCapacity();
    }

    /** SDV enables animal pregnancy only in Big Barn and Deluxe Barn. */
    public boolean allowsAnimalPregnancy() {
        return definition().allowsPregnancy();
    }

    /** SDV's automatic feeding capability belongs to Deluxe Coop/Barn. */
    public boolean hasAutomaticFeed() {
        return definition().automaticFeed();
    }

    public AnimalBuildingTierDefinition definition() {
        return AnimalBuildingTierDefinitions.require(family, tier);
    }

    public String id() {
        return family + "_tier_" + tier;
    }

    public static AnimalBuildingType of(String family, int tier) {
        for (AnimalBuildingType value : values()) {
            if (value.family.equalsIgnoreCase(family) && value.tier == tier) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown building type: family=" + family + ", tier=" + tier);
    }

    public static AnimalBuildingType fromId(String id) {
        for (AnimalBuildingType value : values()) {
            if (value.id().equalsIgnoreCase(id)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown building type id: " + id);
    }
}
