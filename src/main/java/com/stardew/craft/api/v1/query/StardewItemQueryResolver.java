package com.stardew.craft.api.v1.query;

import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Resolves one decoded query payload into item stacks. */
@FunctionalInterface
public interface StardewItemQueryResolver<T> {
    List<ItemStack> resolve(StardewItemQueryContext context, T data);
}
