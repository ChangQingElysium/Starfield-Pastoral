package com.stardew.craft.platform.fabric.extension;

import net.minecraft.world.item.ItemStack;

/** Implemented by items whose abilities depend on their own rules or stack components. */
@FunctionalInterface
public interface ItemAbilityProvider {
    boolean canPerformAction(ItemStack stack, ItemAbility ability);
}
