package com.stardew.craft.forge.registry;

import com.stardew.craft.forge.ForgeBootstrap;
import com.stardew.craft.item.ButterflyPowderItem;

import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Forge 1.20.1 item registrations. Keep this class limited to wiring. */
public final class ForgeItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ForgeBootstrap.MOD_ID);

    public static final RegistryObject<Item> BUTTERFLY_POWDER = ITEMS.register(
            "butterfly_powder",
            () -> new ButterflyPowderItem(new Item.Properties())
    );

    private ForgeItems() {
    }
}
