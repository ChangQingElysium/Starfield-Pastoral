package com.stardew.craft.integration.jei;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Stable content signatures for idempotent JEI runtime refreshes. */
final class JeiRecipeSignatures {
    private JeiRecipeSignatures() {
    }

    static String inputs(List<JeiIngredientStacks.Input> inputs) {
        StringBuilder signature = new StringBuilder();
        for (JeiIngredientStacks.Input input : inputs) {
            signature.append('[').append(input.count()).append(':')
                    .append(stacks(input.stacks())).append(']');
        }
        return signature.toString();
    }

    static String stacks(List<ItemStack> stacks) {
        StringBuilder signature = new StringBuilder();
        for (ItemStack stack : stacks) {
            if (!signature.isEmpty()) signature.append(',');
            signature.append(stack(stack));
        }
        return signature.toString();
    }

    static String stack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "empty";
        return BuiltInRegistries.ITEM.getKey(stack.getItem()) + "x" + stack.getCount()
                + stack.getComponents().toString();
    }
}
