package com.stardew.craft.inventory;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;

public final class ChestMenuActions {
    public static final int FILL_STACKS = 10;
    private ChestMenuActions() {}
    public static boolean handle(AbstractContainerMenu menu, Container chest, int capacity, Player player, int action) {
        if (player.level().isClientSide) return false;
        if (action == FILL_STACKS) {
            if (!menu.getCarried().isEmpty()) return false;
            fillStacks(chest, capacity, player.getInventory());
        } else return false;
        chest.setChanged();
        player.getInventory().setChanged();
        menu.broadcastChanges();
        return true;
    }
    /** Matches SDV FillOutStacks: only initial matching kinds qualify, including full existing stacks. */
    public static void fillStacks(Container chest, int capacity, Container inventory) {
        List<ItemStack> kinds = new ArrayList<>();
        for (int i = 0; i < capacity; i++) if (!chest.getItem(i).isEmpty()) kinds.add(chest.getItem(i).copy());
        for (int source = 0; source < Math.min(36, inventory.getContainerSize()); source++) {
            ItemStack stack = inventory.getItem(source);
            if (stack.isEmpty() || !stack.isStackable() || kinds.stream().noneMatch(k -> ItemStack.isSameItemSameTags(k, stack))) continue;
            for (int i = 0; i < capacity && !stack.isEmpty(); i++) {
                ItemStack target = chest.getItem(i);
                if (target.isEmpty() || !ItemStack.isSameItemSameTags(target, stack) || !chest.canPlaceItem(i, stack)) continue;
                int count = Math.min(stack.getCount(), Math.min(target.getMaxStackSize(), chest.getMaxStackSize()) - target.getCount());
                if (count > 0) { ItemStack merged = target.copy(); merged.grow(count); chest.setItem(i, merged); stack.shrink(count); }
            }
            for (int i = 0; i < capacity && !stack.isEmpty(); i++) if (chest.getItem(i).isEmpty() && chest.canPlaceItem(i, stack)) {
                int count = Math.min(stack.getCount(), Math.min(stack.getMaxStackSize(), chest.getMaxStackSize()));
                chest.setItem(i, stack.copyWithCount(count)); stack.shrink(count);
            }
            inventory.setItem(source, stack.isEmpty() ? ItemStack.EMPTY : stack);
        }
    }
}
