package com.stardew.craft.item.artisan;

import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.quality.QualityHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FlavoredArtisanDrinkItemTest {
    @BeforeEach
    void installOriginalIngredientValues() {
        PreservesIngredientDataManager.applyFromJson("""
                {
                  "grape": {"price":80,"edibility":15,"color":"#7329B5"},
                  "potato": {"price":80,"edibility":10,"color":"#824925"},
                  "ancient_fruit": {"price":550,"edibility":-300,"color":"#00FFFF"}
                }
                """);
    }

    @Test
    void wineUsesOriginalPriceEdibilityColorAndQualityFormulas() {
        ItemStack wine = FlavoredArtisanDrinkItem.createFlavored(PreserveType.WINE,
                new ItemStack(ModItems.GRAPE.get()), new ItemStack(ModItems.WINE.get()));
        FlavoredArtisanDrinkItem item = (FlavoredArtisanDrinkItem) wine.getItem();

        assertEquals(240, item.getSellPrice(wine));
        assertEquals(26, item.getEdibility(wine));
        assertEquals(65, item.getEnergy(wine));
        assertEquals(29, item.getHealth(wine));
        assertEquals(0x7329B5, item.getColor(wine));

        QualityHelper.setQuality(wine, QualityHelper.IRIDIUM);
        assertEquals(480, item.getSellPrice(wine));
        assertEquals(169, item.getEnergy(wine));
        assertEquals(76, item.getHealth(wine));
    }

    @Test
    void juiceAndInedibleFruitUseOriginalFallbackEdibilityFormulas() {
        ItemStack juice = FlavoredArtisanDrinkItem.createFlavored(PreserveType.JUICE,
                new ItemStack(ModItems.POTATO.get()), new ItemStack(ModItems.JUICE.get()));
        FlavoredArtisanDrinkItem juiceItem = (FlavoredArtisanDrinkItem) juice.getItem();
        assertEquals(180, juiceItem.getSellPrice(juice));
        assertEquals(20, juiceItem.getEdibility(juice));
        assertEquals(50, juiceItem.getEnergy(juice));
        assertEquals(22, juiceItem.getHealth(juice));

        ItemStack wine = FlavoredArtisanDrinkItem.createFlavored(PreserveType.WINE,
                new ItemStack(ModItems.ANCIENT_FRUIT.get()), new ItemStack(ModItems.WINE.get()));
        FlavoredArtisanDrinkItem wineItem = (FlavoredArtisanDrinkItem) wine.getItem();
        assertEquals(1650, wineItem.getSellPrice(wine));
        assertEquals(55, wineItem.getEdibility(wine));
        assertEquals(138, wineItem.getEnergy(wine));
        assertEquals(62, wineItem.getHealth(wine));
    }

    @Test
    void legacyRegistryItemRecoversItsFlavorEvenWhenQualityDataReplacesStackCustomData() {
        ResourceLocation grapeId = ResourceLocation.fromNamespaceAndPath("stardewcraft", "grape");
        FlavoredArtisanDrinkItem legacyWine = (FlavoredArtisanDrinkItem)
                ModItems.LEGACY_FLAVORED_DRINKS.get("grape_wine").get();
        ItemStack stack = new ItemStack(legacyWine);

        QualityHelper.setQuality(stack, QualityHelper.IRIDIUM);

        assertEquals(grapeId, FlavoredArtisanDrinkItem.getSourceItemId(stack));
        assertEquals(480, legacyWine.getSellPrice(stack));
        assertEquals(26, legacyWine.getEdibility(stack));
        assertEquals(169, legacyWine.getEnergy(stack));
        assertEquals(76, legacyWine.getHealth(stack));
        assertEquals(0x7329B5, legacyWine.getColor(stack));
    }

    @Test
    void everyRetiredDrinkIdRemainsRegisteredAsACompatibilityItem() {
        assertEquals(31, ModItems.LEGACY_FLAVORED_DRINKS.size());
        assertEquals(11, ModItems.LEGACY_FLAVORED_DRINKS.keySet().stream()
                .filter(id -> id.endsWith("_wine")).count());
        assertEquals(20, ModItems.LEGACY_FLAVORED_DRINKS.keySet().stream()
                .filter(id -> id.endsWith("_juice")).count());
    }
}
