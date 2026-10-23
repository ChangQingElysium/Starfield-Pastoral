package com.stardew.craft.platform.fabric.brewing;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.world.item.ItemStack;

/** Immutable recipes belonging to one native brewing registry, never a global list. */
public record BrewingRecipes(List<IBrewingRecipe> recipes) {
    public static final BrewingRecipes EMPTY = new BrewingRecipes(List.of());

    public BrewingRecipes { recipes = List.copyOf(recipes); }

    public ItemStack output(ItemStack input, ItemStack ingredient) {
        if (input.isEmpty() || input.getCount() != 1 || ingredient.isEmpty()) return ItemStack.EMPTY;
        for (IBrewingRecipe recipe : recipes) {
            ItemStack result = recipe.getOutput(input, ingredient);
            if (!result.isEmpty()) return result;
        }
        return ItemStack.EMPTY;
    }

    public boolean isIngredient(ItemStack stack) {
        return !stack.isEmpty() && recipes.stream().anyMatch(recipe -> recipe.isIngredient(stack));
    }

    public static final class Builder {
        private final List<IBrewingRecipe> recipes = new ArrayList<>();
        public void addRecipe(IBrewingRecipe recipe) { recipes.add(Objects.requireNonNull(recipe)); }
        public BrewingRecipes build() { return new BrewingRecipes(recipes); }
    }

    public interface Access {
        void stardewcraft$recipes(BrewingRecipes recipes);
    }
}
