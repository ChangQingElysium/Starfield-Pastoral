package com.stardew.craft.item.catalog;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.item.ModItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.function.Supplier;

public enum StardewCatalogTab {
    FARMING_FORAGING("stardew_tab", "itemGroup.stardewcraft.stardew", () -> ModItems.PARSNIP.get()),
    MACHINES("machines", "itemGroup.stardewcraft.machines", () -> ModItems.IRIDIUM_SPRINKLER.get()),
    COOKING_ARTISAN("cooking_artisan", "itemGroup.stardewcraft.cooking_artisan", () -> ModItems.STARDROP_TEA.get()),
    FISHING("fishing", "itemGroup.stardewcraft.fishing", () -> ModItems.SALMON.get()),
    MINING("mining", "itemGroup.stardewcraft.mining", () -> ModItems.PRISMATIC_SHARD.get()),
    COMBAT("combat", "itemGroup.stardewcraft.combat", () -> ModItems.GALAXY_SWORD.get()),
    DECOR("decor", "itemGroup.stardewcraft.decor", () -> ModItems.FURNITURE_CATALOGUE.get()),
    NATURE("nature", "itemGroup.stardewcraft.nature", () -> ModItems.GRASS_BLOCK.get()),
    BUILDING("building", "itemGroup.stardewcraft.building", () -> ModItems.TOWN_PAVING.get()),
    SPECIAL("special", "itemGroup.stardewcraft.special", () -> ModItems.STARDROP.get());

    private final String registryName;
    private final String translationKey;
    private final Supplier<? extends Item> iconSupplier;

    StardewCatalogTab(String registryName, String translationKey, Supplier<? extends Item> iconSupplier) {
        this.registryName = registryName;
        this.translationKey = translationKey;
        this.iconSupplier = iconSupplier;
    }

    public String registryName() {
        return registryName;
    }

    public String translationKey() {
        return translationKey;
    }

    public Item iconItem() {
        return iconSupplier.get();
    }

    public ResourceKey<net.minecraft.world.item.CreativeModeTab> key() {
        return ResourceKey.create(Registries.CREATIVE_MODE_TAB,
                new ResourceLocation(StardewCraft.MODID, registryName));
    }
}
