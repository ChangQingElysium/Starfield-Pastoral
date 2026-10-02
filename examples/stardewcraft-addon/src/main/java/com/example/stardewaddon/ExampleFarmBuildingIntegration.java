package com.example.stardewaddon;

import com.stardew.craft.api.v1.agriculture.StardewAnimalFacilities;
import com.stardew.craft.api.v1.building.StardewBuildingFamilies;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import java.util.Map;
import java.util.function.Supplier;

/** Call during addon construction, using the addon's deferred registry suppliers. */
public final class ExampleFarmBuildingIntegration {
    private ExampleFarmBuildingIntegration() {}

    public static void registerFamily(ResourceLocation family, Supplier<? extends Block> manager,
            Supplier<? extends Item> blueprint, Map<Integer,Supplier<? extends Item>> upgrades,
            Map<Integer,StardewBuildingFamilies.Display> portraits) {
        // Register the manager item with StardewBuildingFamilies.managerItem(family, properties),
        // the blueprint with blueprintItem(...), and each permit with upgradeItem(...).
        // Suppliers are evaluated after Forge registration, not during this call.
        StardewBuildingFamilies.register(family,
                new StardewBuildingFamilies.Binding(manager, blueprint, upgrades, portraits));
    }

    public static void registerFacilities(ResourceLocation providerId, StardewAnimalFacilities.Provider provider,
            ResourceLocation upgradeId, ResourceLocation oldBlock, ResourceLocation newBlock) {
        StardewAnimalFacilities.register(providerId, 100, provider);
        StardewAnimalFacilities.registerUpgrade(upgradeId, oldBlock, newBlock, oldState -> {
            CompoundTag next = oldState.copy();
            // This example's replacement keeps the inventory schema. A different schema must
            // translate inventory and components here, without touching the world.
            next.putBoolean("upgraded", true);
            return next;
        });
    }
}
