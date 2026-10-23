package com.stardew.craft.platform.fabric.item;

import net.minecraft.world.item.ItemStack;

/** Direct replacement used by inventory owners, rather than ordinary automation insertion. */
public interface IItemHandlerModifiable extends IItemHandler {
    void setStackInSlot(int slot, ItemStack stack);
}
