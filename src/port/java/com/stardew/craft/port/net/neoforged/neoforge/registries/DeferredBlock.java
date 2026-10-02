package com.stardew.craft.port.net.neoforged.neoforge.registries;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.RegistryObject;

/** PORT(1.20.1): NeoForge {@code DeferredBlock} on top of a Forge {@link RegistryObject}. */
public class DeferredBlock<T extends Block> extends DeferredHolder<Block, T> implements ItemLike {
    public ItemStack toStack() {
        return toStack(1);
    }

    public ItemStack toStack(int count) {
        ItemStack stack = asItem().getDefaultInstance();
        if (stack.isEmpty()) throw new IllegalStateException("Block does not have a corresponding item: " + key);
        stack.setCount(count);
        return stack;
    }

    public static <T extends Block> DeferredBlock<T> createBlock(ResourceLocation key) {
        return createBlock(ResourceKey.create(Registries.BLOCK, key));
    }

    public static <T extends Block> DeferredBlock<T> createBlock(ResourceKey<Block> key) {
        return new DeferredBlock<>(key);
    }

    protected DeferredBlock(ResourceKey<Block> key) {
        super(key);
    }

    DeferredBlock(ResourceKey<Block> key, RegistryObject<Block> object) {
        super(key, object);
    }

    @Override
    public Item asItem() {
        return get().asItem();
    }
}
