package com.stardew.craft.api.v1.communitycenter;

import java.util.List;
import java.util.Objects;

/** Stable DTO that maps directly to the existing Community Center network representation. */
public record StardewBundleDefinition(
        int bundleId,
        int areaId,
        String internalName,
        String displayNameKey,
        String rewardDescriptor,
        List<StardewBundleIngredient> ingredients,
        int color,
        int requiredCount
) {
    public StardewBundleDefinition {
        if (bundleId < 0 || areaId < 0) {
            throw new IllegalArgumentException("Bundle and area IDs cannot be negative");
        }
        internalName = requireText(internalName, "internalName");
        displayNameKey = requireText(displayNameKey, "displayNameKey");
        rewardDescriptor = Objects.requireNonNull(rewardDescriptor, "rewardDescriptor");
        ingredients = List.copyOf(ingredients);
        if (ingredients.isEmpty()) {
            throw new IllegalArgumentException("Bundle ingredients cannot be empty");
        }
        if (requiredCount <= 0 || requiredCount > ingredients.size()) {
            throw new IllegalArgumentException(
                    "Bundle required count must be within its ingredient count");
        }
    }

    public int totalSlots() {
        return ingredients.size();
    }

    private static String requireText(String value, String label) {
        Objects.requireNonNull(value, label);
        if (value.isBlank()) {
            throw new IllegalArgumentException("Bundle " + label + " cannot be blank");
        }
        return value;
    }
}
