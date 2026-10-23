package com.stardew.craft.platform.fabric.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;

public class DeferredBlock<T extends Block> extends DeferredHolder<Block, T> implements ItemLike {
    protected DeferredBlock(ResourceKey<Block> key) {
        super(key);
    }

    public static <T extends Block> DeferredBlock<T> createBlock(ResourceLocation id) {
        return createBlock(ResourceKey.create(Registries.BLOCK, id));
    }

    public static <T extends Block> DeferredBlock<T> createBlock(ResourceKey<Block> key) {
        return new DeferredBlock<>(key);
    }

    @Override
    public Item asItem() {
        return get().asItem();
    }

    public ItemStack toStack() {
        return toStack(1);
    }

    public ItemStack toStack(int count) {
        ItemStack stack = asItem().getDefaultInstance();
        if (stack.isEmpty()) {
            throw new IllegalStateException("Block has no corresponding item: " + key);
        }
        stack.setCount(count);
        return stack;
    }
}
