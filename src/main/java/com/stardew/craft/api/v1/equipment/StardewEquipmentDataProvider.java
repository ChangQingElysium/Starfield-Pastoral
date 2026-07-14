package com.stardew.craft.api.v1.equipment;

import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

@FunctionalInterface
public interface StardewEquipmentDataProvider {
    @Nullable StardewEquipmentData resolve(ItemStack stack);
}
