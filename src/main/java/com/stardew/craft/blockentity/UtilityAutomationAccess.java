package com.stardew.craft.blockentity;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

public interface UtilityAutomationAccess {
    ItemStack getAutomationInput();

    ItemStack getAutomationOutput();

    ItemStack insertAutomation(ItemStack stack, boolean simulate);

    ItemStack extractAutomation(int amount, boolean simulate);

    default boolean isAutomationReady() {
        return !getAutomationOutput().isEmpty();
    }

    default ItemStack getAutomationExtraDrop() {
        return ItemStack.EMPTY;
    }

    default IItemHandler getAutomationItemHandler() {
        return new UtilityItemHandler(this);
    }

    default int getAutomationSlotLimit(int slot) {
        return 64;
    }
}
