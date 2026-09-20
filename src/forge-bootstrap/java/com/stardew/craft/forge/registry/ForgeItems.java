package com.stardew.craft.forge.registry;

import com.stardew.craft.forge.ForgeBootstrap;
import com.stardew.craft.item.ButterflyPowderItem;
import com.stardew.craft.item.SimpleStardewItem;

import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Forge 1.20.1 item registrations. Keep this class limited to wiring. */
public final class ForgeItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ForgeBootstrap.MOD_ID);

    public static final RegistryObject<Item> BUTTERFLY_POWDER = ITEMS.register(
            "butterfly_powder",
            () -> new ButterflyPowderItem(new Item.Properties().stacksTo(999))
    );

    public static final RegistryObject<Item> ECTOPLASM = registerSimple("ectoplasm", "stardewcraft.type.quest", -1);
    public static final RegistryObject<Item> PRISMATIC_JELLY = registerSimple("prismatic_jelly", "stardewcraft.type.quest", -1);
    public static final RegistryObject<Item> EXPLOSIVE_AMMO = registerSimple("explosive_ammo", "stardewcraft.type.resource", 20);
    public static final RegistryObject<Item> CLAY = registerSimple("clay", "stardewcraft.type.resource", 20);
    public static final RegistryObject<Item> FIBER = registerSimple("fiber", "stardewcraft.type.resource", 1);
    public static final RegistryObject<Item> HAY = registerSimple("hay", "stardewcraft.type.resource", 0);
    public static final RegistryObject<Item> WOOD_NORMAL = registerSimple("wood_normal", "stardewcraft.type.resource", 2);
    public static final RegistryObject<Item> WOOD_HARD = registerSimple("wood_hard", "stardewcraft.type.resource", 15);

    private static RegistryObject<Item> registerSimple(String id, String typeKey, int sellPrice) {
        return ITEMS.register(id, () -> new SimpleStardewItem(typeKey, sellPrice, new Item.Properties().stacksTo(999)));
    }

    private ForgeItems() {
    }
}
