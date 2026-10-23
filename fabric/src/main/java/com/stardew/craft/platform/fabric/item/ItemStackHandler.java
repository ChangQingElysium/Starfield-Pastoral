package com.stardew.craft.platform.fabric.item;

import java.util.Objects;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Component-aware owned storage. Valid-operation limits, ownership and NBT layout follow
 * the NeoForge 21.1.217 handler used by the main project, using vanilla 1.21.1 APIs.
 */
public class ItemStackHandler implements IItemHandlerModifiable {
    protected NonNullList<ItemStack> stacks;

    public ItemStackHandler() { this(1); }

    public ItemStackHandler(int size) {
        stacks = NonNullList.withSize(size, ItemStack.EMPTY);
    }

    /** Retains the supplied backing list, matching the original owner's storage contract. */
    public ItemStackHandler(NonNullList<ItemStack> stacks) {
        this.stacks = Objects.requireNonNull(stacks, "Inventory stacks");
    }

    /** Replaces the storage with empty slots; it is not a resize-and-preserve operation. */
    public void setSize(int size) {
        stacks = NonNullList.withSize(size, ItemStack.EMPTY);
    }

    @Override public int getSlots() { return stacks.size(); }

    @Override
    public ItemStack getStackInSlot(int slot) {
        validateSlotIndex(slot);
        return stacks.get(slot);
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        validateSlotIndex(slot);
        stacks.set(slot, Objects.requireNonNull(stack, "Slot stack"));
        onContentsChanged(slot);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack incoming, boolean simulate) {
        if (incoming.isEmpty()) return ItemStack.EMPTY;
        if (!isItemValid(slot, incoming)) return incoming;
        validateSlotIndex(slot);
        ItemStack present = stacks.get(slot);
        if (!present.isEmpty() && !ItemStack.isSameItemSameComponents(present, incoming)) return incoming;

        int available = getStackLimit(slot, incoming) - (present.isEmpty() ? 0 : present.getCount());
        if (available <= 0) return incoming;
        int inserted = Math.min(incoming.getCount(), available);
        if (!simulate) {
            if (present.isEmpty()) {
                stacks.set(slot, inserted == incoming.getCount() ? incoming : incoming.copyWithCount(inserted));
            } else {
                present.grow(inserted);
            }
            onContentsChanged(slot);
        }
        return inserted == incoming.getCount() ? ItemStack.EMPTY
                : incoming.copyWithCount(incoming.getCount() - inserted);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        // Negative requests are invalid, and must never increase storage as a negative split could.
        if (amount <= 0) return ItemStack.EMPTY;
        validateSlotIndex(slot);
        ItemStack present = stacks.get(slot);
        if (present.isEmpty()) return ItemStack.EMPTY;
        int extracted = Math.min(amount, present.getMaxStackSize());
        if (present.getCount() <= extracted) {
            if (simulate) return present.copy();
            stacks.set(slot, ItemStack.EMPTY);
            onContentsChanged(slot);
            return present;
        }
        if (!simulate) {
            stacks.set(slot, present.copyWithCount(present.getCount() - extracted));
            onContentsChanged(slot);
        }
        return present.copyWithCount(extracted);
    }

    @Override public int getSlotLimit(int slot) { return Item.ABSOLUTE_MAX_STACK_SIZE; }
    @Override public boolean isItemValid(int slot, ItemStack stack) { return true; }

    protected int getStackLimit(int slot, ItemStack stack) {
        return Math.min(getSlotLimit(slot), stack.getMaxStackSize());
    }

    public CompoundTag serializeNBT(HolderLookup.Provider registries) {
        ListTag items = new ListTag();
        for (int slot = 0; slot < stacks.size(); slot++) {
            ItemStack stack = stacks.get(slot);
            if (stack.isEmpty()) continue;
            CompoundTag entry = new CompoundTag();
            entry.putInt("Slot", slot);
            items.add(stack.save(registries, entry));
        }
        CompoundTag result = new CompoundTag();
        result.put("Items", items);
        result.putInt("Size", stacks.size());
        return result;
    }

    public void deserializeNBT(HolderLookup.Provider registries, CompoundTag tag) {
        setSize(tag.contains("Size", Tag.TAG_INT) ? tag.getInt("Size") : stacks.size());
        ListTag items = tag.getList("Items", Tag.TAG_COMPOUND);
        for (int index = 0; index < items.size(); index++) {
            CompoundTag entry = items.getCompound(index);
            int slot = entry.getInt("Slot");
            if (slot >= 0 && slot < stacks.size()) {
                ItemStack.parse(registries, entry).ifPresent(stack -> stacks.set(slot, stack));
            }
        }
        onLoad();
    }

    protected void validateSlotIndex(int slot) {
        if (slot < 0 || slot >= stacks.size()) {
            throw new IndexOutOfBoundsException("Slot " + slot + " outside [0," + stacks.size() + ")");
        }
    }

    /** Optional owner notification; loading itself has already restored every valid stack. */
    protected void onLoad() {}

    /** Optional owner notification; insertion/replacement/extraction has already changed storage. */
    protected void onContentsChanged(int slot) {}
}
