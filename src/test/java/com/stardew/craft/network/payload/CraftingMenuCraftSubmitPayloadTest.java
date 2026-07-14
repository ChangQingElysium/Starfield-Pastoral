package com.stardew.craft.network.payload;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CraftingMenuCraftSubmitPayloadTest {
    @Test
    void unqualifiedRecipeIdUsesTheModNamespace() {
        assertEquals("stone_from_any_stone",
                CraftingMenuCraftSubmitPayload.normalizeRecipeId("stone_from_any_stone"));
    }

    @Test
    void modNamespaceIsStoredAsLegacyPath() {
        assertEquals("stone_from_any_stone",
                CraftingMenuCraftSubmitPayload.normalizeRecipeId("stardewcraft:stone_from_any_stone"));
    }

    @Test
    void thirdPartyNamespaceIsPreserved() {
        assertEquals("example:custom_recipe",
                CraftingMenuCraftSubmitPayload.normalizeRecipeId("example:custom_recipe"));
    }
}
