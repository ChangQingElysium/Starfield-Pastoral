package com.stardew.craft.platform.fabric.extension;

import net.minecraft.world.item.ItemStack;

public interface ItemDurabilityProvider {
    boolean isDamageable(ItemStack stack);
}
