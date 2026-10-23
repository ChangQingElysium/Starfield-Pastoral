package com.stardew.craft.platform.fabric.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

public class DeferredItem<T extends Item> extends DeferredHolder<Item, T> implements ItemLike {
    protected DeferredItem(ResourceKey<Item> key) {
        super(key);
    }

    public static <T extends Item> DeferredItem<T> createItem(ResourceLocation id) {
        return createItem(ResourceKey.create(Registries.ITEM, id));
    }

    public static <T extends Item> DeferredItem<T> createItem(ResourceKey<Item> key) {
        return new DeferredItem<>(key);
    }

    @Override
    public Item asItem() {
        return get();
    }

    public ItemStack toStack() {
        return toStack(1);
    }

    public ItemStack toStack(int count) {
        ItemStack stack = get().getDefaultInstance();
        if (stack.isEmpty()) {
            throw new IllegalStateException("Item returned an empty default stack: " + key);
        }
        stack.setCount(count);
        return stack;
    }
}
