package com.stardew.craft.blockentity;

// Upstream-SHA256: 499e446fd51e60a210903df2beb248f72292c0dfd288a821fd52e75af043d9a3

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

@SuppressWarnings("null")
public class UtilityItemHandler implements IItemHandler,
        com.stardew.craft.platform.fabric.item.transfer.TransactionalItemHandler {
    private final UtilityAutomationAccess access;

    public UtilityItemHandler(UtilityAutomationAccess access) {
        this.access = access;
    }

    @Override
    public net.fabricmc.fabric.api.transfer.v1.storage.Storage<net.fabricmc.fabric.api.transfer.v1.item.ItemVariant>
    stardewcraft$itemStorage() {
        if (access instanceof com.stardew.craft.platform.fabric.item.transfer.TransactionalMachineAccess planned) {
            return planned.stardewcraft$automationStorage();
        }
        return com.stardew.craft.platform.fabric.item.transfer.ItemHandlerStorage.unplanned(this);
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
