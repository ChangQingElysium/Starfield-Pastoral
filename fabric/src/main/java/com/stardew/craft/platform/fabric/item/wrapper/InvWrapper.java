package com.stardew.craft.platform.fabric.item.wrapper;

import com.stardew.craft.platform.fabric.item.IItemHandlerModifiable;
import java.util.Objects;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/** A live view over a vanilla Container, including its placement rules and dirty notifications. */
public class InvWrapper implements IItemHandlerModifiable {
    private final Container inventory;

    public InvWrapper(Container inventory) {
        this.inventory = Objects.requireNonNull(inventory, "Container");
    }

    public Container getInv() { return inventory; }
    @Override public int getSlots() { return inventory.getContainerSize(); }
    @Override public ItemStack getStackInSlot(int slot) { return inventory.getItem(slot); }
    @Override public int getSlotLimit(int slot) { return inventory.getMaxStackSize(); }
    @Override public boolean isItemValid(int slot, ItemStack stack) { return inventory.canPlaceItem(slot, stack); }

    @Override
    public ItemStack insertItem(int slot, ItemStack incoming, boolean simulate) {
        if (incoming.isEmpty()) return ItemStack.EMPTY;
        ItemStack present = inventory.getItem(slot);
        if (!present.isEmpty() && (present.getCount() >= Math.min(present.getMaxStackSize(), getSlotLimit(slot))
                || !ItemStack.isSameItemSameComponents(present, incoming))) return incoming;
        if (!inventory.canPlaceItem(slot, incoming)) return incoming;
        int room = Math.min(incoming.getMaxStackSize(), getSlotLimit(slot))
                - (present.isEmpty() ? 0 : present.getCount());
        int inserted = Math.min(incoming.getCount(), room);
        if (!simulate) {
            ItemStack replacement;
            if (present.isEmpty() && inserted == incoming.getCount()) {
                replacement = incoming;
            } else {
                replacement = incoming.copyWithCount(inserted + (present.isEmpty() ? 0 : present.getCount()));
            }
            inventory.setItem(slot, replacement);
            inventory.setChanged();
        }
        return inserted == incoming.getCount() ? ItemStack.EMPTY
                : incoming.copyWithCount(incoming.getCount() - inserted);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount <= 0) return ItemStack.EMPTY;
        ItemStack present = inventory.getItem(slot);
        if (present.isEmpty()) return ItemStack.EMPTY;
        int extracted = Math.min(present.getCount(), amount);
        if (simulate) return present.copyWithCount(extracted);
        // Deliberately use the Container operation and its limit semantics. This differs from
        // ItemStackHandler's per-item max extraction cap, just as in the original wrapper.
        ItemStack result = inventory.removeItem(slot, extracted);
        inventory.setChanged();
        return result;
    }

    @Override public void setStackInSlot(int slot, ItemStack stack) { inventory.setItem(slot, stack); }

    @Override
    public boolean equals(Object other) {
        return this == other || (other != null && other.getClass() == getClass()
                && inventory.equals(((InvWrapper) other).inventory));
    }

    @Override public int hashCode() { return inventory.hashCode(); }
}
