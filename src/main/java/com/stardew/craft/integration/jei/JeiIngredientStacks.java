package com.stardew.craft.integration.jei;

import com.stardew.craft.api.v1.item.StardewItemDataApi;
import com.stardew.craft.api.v1.production.StardewCookingIngredient;
import com.stardew.craft.item.catalog.StardewItemCatalog;
import com.stardew.craft.item.catalog.StardewItemComparator;
import com.stardew.craft.item.catalog.StardewItemDisplayStacks;
import com.stardew.craft.player.StardewCraftingRecipeData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Builds the complete set of stacks that JEI must associate with one logical ingredient. */
public final class JeiIngredientStacks {
    private JeiIngredientStacks() {
    }

    public record Input(List<ItemStack> stacks, int count) {
        public Input {
            count = Math.max(1, count);
            List<ItemStack> normalized = new ArrayList<>();
            for (ItemStack source : stacks == null ? List.<ItemStack>of() : stacks) {
                if (source == null || source.isEmpty()) continue;
                ItemStack stack = source.copy();
                stack.setCount(count);
                if (normalized.stream().noneMatch(existing -> ItemStack.isSameItemSameComponents(existing, stack))) {
                    normalized.add(stack);
                }
            }
            normalized.sort(StardewItemComparator.STACK);
            stacks = List.copyOf(normalized);
        }

        public boolean isEmpty() {
            return stacks.isEmpty();
        }
    }

    public static Input crafting(StardewCraftingRecipeData.IngredientEntry ingredient) {
        if (ingredient == null) return new Input(List.of(), 1);
        List<Item> items = new ArrayList<>();
        if (ingredient.tag() != null && !ingredient.tag().isBlank()) {
            ResourceLocation tagId = ResourceLocation.tryParse(ingredient.tag());
            if (tagId != null) {
                BuiltInRegistries.ITEM.getTagOrEmpty(TagKey.create(Registries.ITEM, tagId))
                        .forEach(holder -> items.add(holder.value()));
            }
        } else {
            addItem(items, ingredient.item());
        }
        return new Input(expandDisplayStacks(items), ingredient.count());
    }

    public static Input cooking(StardewCookingIngredient ingredient) {
        if (ingredient == null) return new Input(List.of(), 1);
        List<Item> items = new ArrayList<>();
        ingredient.item().ifPresent(id -> addItem(items, id));
        ingredient.tag().ifPresent(tag -> BuiltInRegistries.ITEM
                .getTagOrEmpty(TagKey.create(Registries.ITEM, tag))
                .forEach(holder -> items.add(holder.value())));
        if (!ingredient.categories().isEmpty()) {
            for (Item item : StardewItemCatalog.visibleItems()) {
                ItemStack stack = new ItemStack(item);
                if (StardewItemDataApi.resolve(stack)
                        .map(data -> ingredient.categories().contains(data.category()))
                        .orElse(false)) {
                    items.add(item);
                }
            }
        }
        return new Input(expandDisplayStacks(items), ingredient.count());
    }

    private static List<ItemStack> expandDisplayStacks(List<Item> items) {
        List<ItemStack> stacks = new ArrayList<>();
        items.stream().distinct().sorted(StardewItemComparator.ITEM)
                .forEach(item -> stacks.addAll(StardewItemDisplayStacks.stacksForItem(item)));
        return stacks;
    }

    private static void addItem(List<Item> items, String rawId) {
        ResourceLocation id = rawId == null ? null : ResourceLocation.tryParse(rawId);
        if (id != null) addItem(items, id);
    }

    private static void addItem(List<Item> items, ResourceLocation id) {
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return;
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item != net.minecraft.world.item.Items.AIR) items.add(item);
    }
}
