package com.stardew.craft.port.net.minecraft.world.item.component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/**
 * 1.21 {@code minecraft:container}; stored as the vanilla 1.20.1 container layout
 * {@code BlockEntityTag.Items: [{Slot:b, id, Count, tag}]} (non-empty slots only), the same layout
 * shulker boxes and other container block items use.
 */
public final class ItemContainerContents {
    public static final int MAX_SIZE = 256;
    public static final ItemContainerContents EMPTY = new ItemContainerContents(NonNullList.create());

    private final NonNullList<ItemStack> items;

    private ItemContainerContents(NonNullList<ItemStack> items) {
        if (items.size() > MAX_SIZE) {
            throw new IllegalArgumentException("Got " + items.size() + " items, but maximum is " + MAX_SIZE);
        }
        this.items = items;
    }

    public static ItemContainerContents fromItems(List<ItemStack> stacks) {
        int last = lastNonEmpty(stacks);
        if (last == -1) {
            return EMPTY;
        }
        NonNullList<ItemStack> copy = NonNullList.withSize(last + 1, ItemStack.EMPTY);
        for (int i = 0; i <= last; i++) {
            copy.set(i, stacks.get(i).copy());
        }
        return new ItemContainerContents(copy);
    }

    private static int lastNonEmpty(List<ItemStack> stacks) {
        for (int i = stacks.size() - 1; i >= 0; i--) {
            if (!stacks.get(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    public void copyInto(NonNullList<ItemStack> target) {
        for (int i = 0; i < target.size(); i++) {
            target.set(i, i < items.size() ? items.get(i).copy() : ItemStack.EMPTY);
        }
    }

    public ItemStack copyOne() {
        return items.isEmpty() ? ItemStack.EMPTY : items.get(0).copy();
    }

    public Stream<ItemStack> stream() {
        return items.stream().map(ItemStack::copy);
    }

    public Stream<ItemStack> nonEmptyStream() {
        return items.stream().filter(stack -> !stack.isEmpty()).map(ItemStack::copy);
    }

    public Iterable<ItemStack> nonEmptyItems() {
        return items.stream().filter(stack -> !stack.isEmpty())::iterator;
    }

    public Iterable<ItemStack> nonEmptyItemsCopy() {
        return nonEmptyStream()::iterator;
    }

    /** PORT(1.20.1): vanilla ContainerHelper item list layout. */
    public ListTag toItemsTag() {
        ListTag list = new ListTag();
        for (int slot = 0; slot < items.size(); slot++) {
            ItemStack stack = items.get(slot);
            if (!stack.isEmpty()) {
                CompoundTag entry = new CompoundTag();
                entry.putByte("Slot", (byte) slot);
                stack.save(entry);
                list.add(entry);
            }
        }
        return list;
    }

    /** PORT(1.20.1): inverse of {@link #toItemsTag()}. */
    public static ItemContainerContents fromItemsTag(ListTag list) {
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            int slot = entry.contains("Slot", Tag.TAG_ANY_NUMERIC) ? entry.getByte("Slot") & 255 : i;
            if (slot >= MAX_SIZE) {
                continue;
            }
            while (stacks.size() <= slot) {
                stacks.add(ItemStack.EMPTY);
            }
            stacks.set(slot, ItemStack.of(entry));
        }
        return fromItems(stacks);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ItemContainerContents contents) || contents.items.size() != items.size()) {
            return false;
        }
        for (int i = 0; i < items.size(); i++) {
            if (!ItemStack.matches(items.get(i), contents.items.get(i))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 0;
        for (ItemStack stack : items) {
            hash = hash * 31 + (stack.isEmpty() ? 0 : stack.getItem().hashCode() * 31 + stack.getCount());
        }
        return hash;
    }
}
