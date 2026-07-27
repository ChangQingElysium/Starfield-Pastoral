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
        int buildDays,
        int money,
        List<Material> materials
) {
    public AnimalBuildingTierDefinition {
        family = family.trim().toLowerCase(java.util.Locale.ROOT);
        if (family.isBlank() || tier < 1 || capacity < 0
                || hayCapacity < 0 || buildDays < 0 || money < 0) {
            throw new IllegalArgumentException(
                    "Invalid animal-building tier definition "
                            + family + ":" + tier);
        }
        materials = List.copyOf(materials);
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
}
