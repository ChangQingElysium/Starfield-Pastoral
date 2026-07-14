package com.stardew.craft.player;

import com.stardew.craft.cooking.service.VanillaCookingRecipeData;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Known recipe catalog used for player-owned recipe systems.
 *
 * Uses the synchronized crafting and cooking definition registries.
 */
public final class RecipeCatalogData {
    private RecipeCatalogData() {
    }

    public static List<String> getCraftingRecipeIds() {
        return StardewCraftingRecipeData.getRecipeIds();
    }

    public static List<String> getCookingRecipeIds() {
        return VanillaCookingRecipeData.getRecipeIds().stream()
                .map(VanillaCookingRecipeData::storageId)
                .toList();
    }

    public static List<String> getRecipeIds(String category) {
        if (category == null || category.isBlank()) {
            return List.of();
        }

        String normalized = category.toLowerCase(Locale.ROOT);
        if ("cooking".equals(normalized)) {
            return getCookingRecipeIds();
        }
        if ("crafting".equals(normalized)) {
            return getCraftingRecipeIds();
        }
        return List.of();
    }

    public static List<String> getAllKnownRecipeIds() {
        Set<String> all = new LinkedHashSet<>();
        all.addAll(getCraftingRecipeIds());
        all.addAll(getCookingRecipeIds());
        return List.copyOf(all);
    }

}
