package com.stardew.craft.platform.fabric.item;

import net.minecraft.world.item.ItemStack;

/** Project-owned inventory view used by gameplay; this is not a Fabric Transfer Storage. */
public interface IItemHandler {
    int getSlots();

    /** Borrowed read-only view. Mutating the returned stack bypasses the inventory's rules. */
    ItemStack getStackInSlot(int slot);

    /** Leaves the argument unchanged and returns the portion that could not be inserted. */
    ItemStack insertItem(int slot, ItemStack stack, boolean simulate);

    /** The caller owns the returned stack; simulation must not remove inventory contents. */
    ItemStack extractItem(int slot, int amount, boolean simulate);

    int getSlotLimit(int slot);

    /** Checks the slot's item rule, independently of whether it is currently full. */
    boolean isItemValid(int slot, ItemStack stack);
}
