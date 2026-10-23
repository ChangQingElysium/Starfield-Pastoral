package com.stardew.craft.platform.fabric.brewing;

import net.minecraft.world.item.ItemStack;

public interface IBrewingRecipe {
    boolean isInput(ItemStack input);
    boolean isIngredient(ItemStack ingredient);
    ItemStack getOutput(ItemStack input, ItemStack ingredient);
}
