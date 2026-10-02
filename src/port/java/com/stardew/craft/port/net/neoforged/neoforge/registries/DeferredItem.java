package com.stardew.craft.port.net.neoforged.neoforge.registries;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.registries.RegistryObject;

/** PORT(1.20.1): NeoForge {@code DeferredItem} on top of a Forge {@link RegistryObject}. */
public class DeferredItem<T extends Item> extends DeferredHolder<Item, T> implements ItemLike {
    public ItemStack toStack() {
        return toStack(1);
    }

    public ItemStack toStack(int count) {
        ItemStack stack = asItem().getDefaultInstance();
        if (stack.isEmpty()) throw new IllegalStateException("Obtained empty item stack; incorrect getDefaultInstance() call?");
        stack.setCount(count);
        return stack;
    }

    public static <T extends Item> DeferredItem<T> createItem(ResourceLocation key) {
        return createItem(ResourceKey.create(Registries.ITEM, key));
    }

    public static <T extends Item> DeferredItem<T> createItem(ResourceKey<Item> key) {
        return new DeferredItem<>(key);
    }

    protected DeferredItem(ResourceKey<Item> key) {
        super(key);
    }

    DeferredItem(ResourceKey<Item> key, RegistryObject<Item> object) {
        super(key, object);
    }

    @Override
    public Item asItem() {
        return get();
    }
}
