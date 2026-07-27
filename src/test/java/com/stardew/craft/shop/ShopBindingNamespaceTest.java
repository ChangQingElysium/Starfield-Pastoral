package com.stardew.craft.shop;

import com.stardew.craft.api.v1.shop.StardewShopBinding;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShopBindingNamespaceTest {
    @Test
    void addonBareReferencesUseOwningNamespace() {
        StardewShopBinding normalized = ShopDataLoader.normalizeBinding(
                id("example_addon", "orchard_archivist"),
                binding("orchard_stand", "Archivist"));

        assertEquals("example_addon:orchard_stand", normalized.shop());
        assertEquals(Optional.of("example_addon:archivist"), normalized.npc());
    }

    @Test
    void explicitAndCoreLegacyReferencesRemainCompatible() {
        StardewShopBinding explicit = ShopDataLoader.normalizeBinding(
                id("example_addon", "lewis_shop"),
                binding("stardewcraft:seed_shop", "stardewcraft:Lewis"));
        assertEquals("stardewcraft:seed_shop", explicit.shop());
        assertEquals(Optional.of("stardewcraft:lewis"), explicit.npc());

        StardewShopBinding coreLegacy = ShopDataLoader.normalizeBinding(
                id("stardewcraft", "seed_shop"),
                binding("SeedShop", "Lewis"));
        assertEquals("SeedShop", coreLegacy.shop());
        assertEquals(Optional.of("lewis"), coreLegacy.npc());
    }

    private static StardewShopBinding binding(String shop, String npc) {
        return new StardewShopBinding(
                shop,
                Optional.of(npc),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                List.of());
    }

    private static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }
}
