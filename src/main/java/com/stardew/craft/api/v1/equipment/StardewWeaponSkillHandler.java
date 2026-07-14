package com.stardew.craft.api.v1.equipment;

import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.ItemStack;

@FunctionalInterface
public interface StardewWeaponSkillHandler {
    InteractionResultHolder<ItemStack> use(StardewWeaponSkillContext context);
}
