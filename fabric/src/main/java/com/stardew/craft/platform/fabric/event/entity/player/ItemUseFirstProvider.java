package com.stardew.craft.platform.fabric.event.entity.player;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

/** Explicit early-use contract for the existing surface-floor item, before block interaction. */
public interface ItemUseFirstProvider {
    InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context);
}
