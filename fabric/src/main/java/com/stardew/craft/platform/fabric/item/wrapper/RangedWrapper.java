package com.stardew.craft.platform.fabric.item.wrapper;

import com.stardew.craft.platform.fabric.item.IItemHandlerModifiable;
import java.util.Objects;
import net.minecraft.world.item.ItemStack;

/** A bounded slot projection; out-of-view insertion is rejected without consuming the argument. */
public class RangedWrapper implements IItemHandlerModifiable {
    private final IItemHandlerModifiable inventory;
    private final int first;
    private final int end;

    public RangedWrapper(IItemHandlerModifiable inventory, int first, int endExclusive) {
        if (first < 0 || endExclusive <= first || endExclusive > inventory.getSlots()) {
            throw new IllegalArgumentException("Invalid inventory slot range [" + first + "," + endExclusive + ")");
        }
        this.inventory = Objects.requireNonNull(inventory, "Inventory");
        this.first = first;
        this.end = endExclusive;
    }

    @Override public int getSlots() { return end - first; }
    private boolean contains(int slot) { return slot >= 0 && slot < getSlots(); }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return contains(slot) ? inventory.getStackInSlot(slot + first) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        return contains(slot) ? inventory.insertItem(slot + first, stack, simulate) : stack;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return contains(slot) ? inventory.extractItem(slot + first, amount, simulate) : ItemStack.EMPTY;
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        if (contains(slot)) inventory.setStackInSlot(slot + first, stack);
    }

    @Override public int getSlotLimit(int slot) { return contains(slot) ? inventory.getSlotLimit(slot + first) : 0; }
    @Override public boolean isItemValid(int slot, ItemStack stack) {
        return contains(slot) && inventory.isItemValid(slot + first, stack);
    }
}
