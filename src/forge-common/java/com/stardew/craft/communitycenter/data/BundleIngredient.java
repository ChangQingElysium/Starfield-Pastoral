package com.stardew.craft.communitycenter.data;

import javax.annotation.Nullable;

/**
 * A single ingredient requirement within a Bundle.
 * Mirrors SDV's BundleIngredientDescription struct.
 *
 * @param itemId   mod item ResourceLocation path (e.g. "parsnip"), or null for gold bundles
 * @param sdvId    original SDV numeric item ID string (for display/fallback)
 * @param category negative number for category match (-1 = gold), 0 = specific item
 * @param stack    required quantity
 * @param quality  minimum quality (0=normal, 1=silver, 2=gold, 3=iridium)
 */
public record BundleIngredient(
        @Nullable String itemId,
        String sdvId,
        int category,
        int stack,
        int quality
) {
    /** Whether this ingredient is a gold/money requirement (Vault bundles). */
    public boolean isMoneyIngredient() {
        return category == -1;
    }

    /** The gold amount required (only valid when {@link #isMoneyIngredient()} is true). */
    public int moneyRequired() {
        return stack;
    }
}
