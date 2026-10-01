package com.stardew.craft.blockentity;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

@SuppressWarnings("null")
public class UtilityItemHandler implements IItemHandler {
    private final UtilityAutomationAccess access;

    public UtilityItemHandler(UtilityAutomationAccess access) {
        this.access = access;
    }

    @Override
    public int getSlots() {
        return 2;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return slot == 0 ? access.getAutomationInput() : access.getAutomationOutput();
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (slot != 0 || stack.isEmpty()) {
            return stack;
        }
        return access.insertAutomation(stack, simulate);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (slot != 1 || amount <= 0) {
            return ItemStack.EMPTY;
        }
        return access.extractAutomation(amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        return access.getAutomationSlotLimit(slot);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return slot == 0;
    }
}
