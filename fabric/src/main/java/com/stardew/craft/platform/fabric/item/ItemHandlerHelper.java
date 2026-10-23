package com.stardew.craft.platform.fabric.item;

import net.minecraft.world.item.ItemStack;

/** The insertion helpers currently used by StardewCraft. */
public final class ItemHandlerHelper {
    public static ItemStack insertItem(IItemHandler inventory, ItemStack stack, boolean simulate) {
        if (inventory == null || stack.isEmpty()) return stack;
        ItemStack remainder = stack;
        for (int slot = 0; slot < inventory.getSlots() && !remainder.isEmpty(); slot++) {
            remainder = inventory.insertItem(slot, remainder, simulate);
        }
        return remainder;
    }

    /** Merges matching components before trying empty slots, matching player pickup ordering. */
    public static ItemStack insertItemStacked(IItemHandler inventory, ItemStack stack, boolean simulate) {
        if (inventory == null || stack.isEmpty()) return stack;
        if (!stack.isStackable()) return insertItem(inventory, stack, simulate);
        ItemStack remainder = stack;
        int slots = inventory.getSlots();
        for (int slot = 0; slot < slots && !remainder.isEmpty(); slot++) {
            if (ItemStack.isSameItemSameComponents(inventory.getStackInSlot(slot), remainder)) {
                remainder = inventory.insertItem(slot, remainder, simulate);
            }
        }
        for (int slot = 0; slot < slots && !remainder.isEmpty(); slot++) {
            if (inventory.getStackInSlot(slot).isEmpty()) {
                remainder = inventory.insertItem(slot, remainder, simulate);
            }
        }
        return remainder;
    }

    private ItemHandlerHelper() {}
}
