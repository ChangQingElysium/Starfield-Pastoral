package com.stardew.craft.platform.fabric.item.damage;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class ItemDamageHooks {
    private ItemDamageHooks() {}

    /** The item may veto damage; an allowance still passes through all native component and entity checks. */
    public static boolean allowsDamage(Item item, ItemStack stack, DamageSource source) {
        return !(item instanceof ItemDamageProvider provider) || provider.canBeHurtBy(stack, source);
    }
}
