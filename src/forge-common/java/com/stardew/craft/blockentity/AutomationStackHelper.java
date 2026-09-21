package com.stardew.craft.blockentity;

import net.minecraft.world.item.ItemStack;

public final class AutomationStackHelper {
    private AutomationStackHelper() {
    }

    public static ItemStack remainderAfterInsert(ItemStack stack, int consume) {
        if (stack.isEmpty() || consume <= 0) {
            return stack;
        }
        if (stack.getCount() <= consume) {
            return ItemStack.EMPTY;
        }
        ItemStack remainder = stack.copy();
        remainder.setCount(stack.getCount() - consume);
        return remainder;
    }

    public static ItemStack extractUpTo(ItemStack stack, int amount) {
        if (stack.isEmpty() || amount <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack out = stack.copy();
        out.setCount(Math.min(amount, out.getCount()));
        return out;
    }
}
