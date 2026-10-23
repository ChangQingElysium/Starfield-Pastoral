package com.stardew.craft.platform.fabric.item.damage;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;

/** Controls damage to a dropped item entity, independently of held-item durability. */
public interface ItemDamageProvider {
    default boolean canBeHurtBy(ItemStack stack, DamageSource source) { return true; }
}
