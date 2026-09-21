package com.stardew.craft.communitycenter.data;

import java.util.List;

/**
 * Immutable definition of a single Bundle, loaded from bundles.json.
 * Mirrors SDV's Bundle data structure from Data/Bundles.
 *
 * @param bundleId       unique bundle ID (0-36)
 * @param areaId         community center area (0-6)
 * @param internalName   original SDV name (e.g. "Spring Crops")
 * @param displayNameKey i18n key for display name
 * @param rewardString   reward descriptor (e.g. "O 465 20")
 * @param ingredients    list of required ingredients
 * @param color          bundle color index (0-6, maps to ball sprite)
 * @param requiredCount  number of ingredient slots that must be filled to complete
 */
public record BundleDefinition(
        int bundleId,
        int areaId,
        String internalName,
        String displayNameKey,
        String rewardString,
        List<BundleIngredient> ingredients,
        int color,
        int requiredCount
) {
    /** Total number of ingredient slots (may exceed requiredCount). */
    public int totalSlots() {
        return ingredients.size();
    }

    /** Whether this is a Vault (money) bundle. */
    public boolean isVaultBundle() {
        return areaId == 4;
    }
}
