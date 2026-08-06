package com.stardew.craft.animal.model;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Data-pack definition for one animal-building tier. */
public record AnimalBuildingTierDefinition(
        ResourceLocation dataId,
        boolean replacesExisting,
        String family,
        int tier,
        int capacity,
        int hayCapacity,
        boolean allowsPregnancy,
        boolean automaticFeed,
        int money,
        List<Material> materials,
        Validation validation
) {
    public AnimalBuildingTierDefinition {
        family = family.trim().toLowerCase(java.util.Locale.ROOT);
        if (family.isBlank() || tier < 1 || capacity < 0
                || hayCapacity < 0 || money < 0) {
            throw new IllegalArgumentException(
                    "Invalid animal-building tier definition "
                            + family + ":" + tier);
        }
        materials = List.copyOf(materials);
        if (validation == null) {
            throw new IllegalArgumentException("Building validation definition is required");
        }
    }

    public String key() {
        return family + ":" + tier;
    }

    public record Material(ResourceLocation item, int count) {
        public Material {
            if (item == null || count <= 0) {
                throw new IllegalArgumentException(
                        "Building material requires an item and positive count");
            }
        }
    }

    public record Validation(
            int scanRangeXZ,
            int scanRangeUp,
            int scanRangeDown,
            int feedTroughs,
            int autoFeedTroughs,
            int hayHoppers,
            int incubators,
            int minInteriorBlocks,
            boolean requireEnclosed,
            boolean requireDoor,
            int minDoorCount
    ) {
        public Validation {
            if (scanRangeXZ < 0 || scanRangeUp < 0 || scanRangeDown < 0
                    || feedTroughs < 0 || autoFeedTroughs < 0 || hayHoppers < 0
                    || incubators < 0 || minInteriorBlocks < 0 || minDoorCount < 0) {
                throw new IllegalArgumentException("Building validation values cannot be negative");
            }
        }
    }
}
