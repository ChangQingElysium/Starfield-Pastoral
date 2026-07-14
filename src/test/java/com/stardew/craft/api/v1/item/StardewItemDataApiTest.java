package com.stardew.craft.api.v1.item;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StardewItemDataApiTest {
    @Test
    void legacyToolTranslationKeysMapToTheToolCategory() {
        assertEquals(
                ResourceLocation.fromNamespaceAndPath("stardewcraft", "tool"),
                StardewItemDataApi.legacyCategory("stardewcraft.tool.watering_can"));
    }

    @Test
    void modernBuiltInAndAddonCategoriesKeepTheirMeaning() {
        assertEquals(
                ResourceLocation.fromNamespaceAndPath("stardewcraft", "crop"),
                StardewItemDataApi.legacyCategory("stardewcraft.type.crop"));
        assertEquals(
                ResourceLocation.fromNamespaceAndPath("example", "custom"),
                StardewItemDataApi.legacyCategory("example:custom"));
    }
}
