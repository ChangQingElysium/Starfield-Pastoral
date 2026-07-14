package com.stardew.craft.item.catalog;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.item.StardewItemDataApi;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.artisan.ArtisanRecipeDataManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public final class StardewItemCatalog {
    private static final List<String> FARMING_TYPES = List.of(
            "stardewcraft.type.seed",
            "stardewcraft.type.crop_seed",
            "stardewcraft.type.crop",
            "stardewcraft.type.forage",
            "stardewcraft.type.fruit",
            "stardewcraft.type.animal_product",
            "stardewcraft.type.fertilizer"
    );

    private static final List<String> MACHINE_TYPES = List.of(
            "stardewcraft.type.tool",
            "stardewcraft.type.utility",
            "stardewcraft.type.magic",
            "stardewcraft.type.craftable"
    );

    private static final List<String> COOKING_TYPES = List.of(
            "stardewcraft.type.cooking_ingredient",
            "stardewcraft.type.cooking",
            "stardewcraft.type.festival_food",
            "stardewcraft.type.artisan_goods",
            "stardewcraft.type.artisan_animal_quality"
    );

    private static final List<String> FISHING_TYPES = List.of(
            "stardewcraft.type.fishing",
            "stardewcraft.type.fish",
            "stardewcraft.type.fish_quality",
            "stardewcraft.type.crabpot",
            "stardewcraft.type.legendary_fish",
            "stardewcraft.type.trash"
    );

    private static final List<String> MINING_TYPES = List.of(
            "stardewcraft.type.resource",
            "stardewcraft.type.gem",
            "stardewcraft.type.mineral",
            "stardewcraft.type.artifact",
            "stardewcraft.type.artifact_quality",
            "stardewcraft.type.metal_bar",
            "stardewcraft.type.syrup"
    );

    private static final List<String> COMBAT_TYPES = List.of(
            "stardewcraft.type.weapon",
            "stardewcraft.type.weapon.sword",
            "stardewcraft.type.weapon.dagger",
            "stardewcraft.type.weapon.club",
            "stardewcraft.type.weapon.slingshot",
            "stardewcraft.type.ring",
            "stardewcraft.type.boots",
            "stardewcraft.type.hat",
            "stardewcraft.type.shirt",
            "stardewcraft.type.pants",
            "stardewcraft.type.trinket",
            "stardewcraft.type.monster_loot"
    );

    private static final List<String> DECOR_TYPES = List.of(
            "stardewcraft.type.furniture",
            "stardewcraft.type.furniture_painting",
            "stardewcraft.type.festival_decoration",
            "stardewcraft.type.scarecrow",
            "stardewcraft.type.carpet"
    );

    private static final List<String> SPECIAL_TYPES = List.of(
            "stardewcraft.type.book",
            "stardewcraft.type.quest",
            "stardewcraft.type.special",
            "stardewcraft.type.misc"
    );

    private static final Map<StardewCatalogTab, List<String>> TYPE_ORDER = Map.of(
            StardewCatalogTab.FARMING_FORAGING, FARMING_TYPES,
            StardewCatalogTab.MACHINES, MACHINE_TYPES,
            StardewCatalogTab.COOKING_ARTISAN, COOKING_TYPES,
            StardewCatalogTab.FISHING, FISHING_TYPES,
            StardewCatalogTab.MINING, MINING_TYPES,
            StardewCatalogTab.COMBAT, COMBAT_TYPES,
            StardewCatalogTab.DECOR, DECOR_TYPES,
            StardewCatalogTab.SPECIAL, SPECIAL_TYPES
    );

    private static boolean auditedFallbackItems;

    private StardewItemCatalog() {
    }

    public static void acceptTab(StardewCatalogTab tab, CreativeModeTab.Output output) {
        auditFallbackItems();
        for (Item item : visibleItems()) {
            if (tabForItem(item) != tab) {
                continue;
            }
            for (ItemStack stack : StardewItemDisplayStacks.stacksForItem(item)) {
                output.accept(stack);
            }
        }

        if (tab == StardewCatalogTab.COOKING_ARTISAN) {
            StardewItemDisplayStacks.preserveVariants().forEach(output::accept);
        } else if (tab == StardewCatalogTab.FISHING) {
            StardewItemDisplayStacks.specificBaitVariants().forEach(output::accept);
        } else if (tab == StardewCatalogTab.SPECIAL) {
            StardewItemDisplayStacks.junimoNoteAreaVariants().forEach(output::accept);
        }
    }

    public static List<Item> visibleItems() {
        List<Item> items = new ArrayList<>();
        Set<Item> seen = new HashSet<>();
        for (var holder : ModItems.ITEMS.getEntries()) {
            Item item = holder.get();
            if (!seen.add(item) || item == net.minecraft.world.item.Items.AIR) {
                continue;
            }
            if ("stardewcraft.type.hidden".equals(typeKey(item))) {
                continue;
            }
            if (StardewItemDisplayStacks.isHiddenBaseItem(item)) {
                continue;
            }
            items.add(item);
        }
        items.sort(StardewItemComparator.ITEM);
        return items;
    }

    public static List<ItemStack> jeiExtraIngredientStacks() {
        List<ItemStack> stacks = new ArrayList<>();
        for (Item item : visibleItems()) {
            List<ItemStack> displayStacks = StardewItemDisplayStacks.stacksForItem(item);
            if (displayStacks.size() > 1) {
                stacks.addAll(displayStacks);
            }
        }
        stacks.addAll(StardewItemDisplayStacks.preserveVariants());
        stacks.addAll(StardewItemDisplayStacks.specificBaitVariants());
        stacks.sort(StardewItemComparator.STACK);
        return stacks;
    }

    public static List<Item> specificBaitTargetItems() {
        return visibleItems().stream()
                .filter(item -> StardewItemDisplayStacks.isSpecificBaitTargetType(typeKey(item)))
                .toList();
    }

    public static List<Item> itemsForDynamicInput(ArtisanRecipeDataManager.InputMode inputMode) {
        Predicate<Item> predicate = switch (inputMode) {
            case CROP_TYPE -> item -> "stardewcraft.type.crop".equals(typeKey(item));
            case FISH_TYPE -> item -> StardewItemDisplayStacks.isSpecificBaitTargetType(typeKey(item));
            case MINERAL_TYPE -> item -> "stardewcraft.type.mineral".equals(typeKey(item));
            default -> item -> false;
        };
        return visibleItems().stream().filter(predicate).toList();
    }

    public static StardewCatalogTab tabForItem(Item item) {
        if (item == ModItems.JUNIMO_NOTE.get()) {
            return StardewCatalogTab.SPECIAL;
        }

        StardewCatalogTab typedTab = tabForType(typeKey(item));
        return typedTab == null ? StardewCatalogTab.SPECIAL : typedTab;
    }

    public static int sectionOrder(StardewCatalogTab tab, Item item) {
        String typeKey = typeKey(item);
        List<String> order = TYPE_ORDER.get(tab);
        if (order == null) {
            return Integer.MAX_VALUE;
        }
        if (typeKey.startsWith("stardewcraft.tool.")) {
            return order.indexOf("stardewcraft.type.tool");
        }
        int index = order.indexOf(typeKey);
        return index >= 0 ? index : order.size();
    }

    public static String typeKey(Item item) {
        return StardewItemDataApi.getTypeKey(new ItemStack(item));
    }

    private static StardewCatalogTab tabForType(String typeKey) {
        if (typeKey.isBlank()) {
            return null;
        }
        if (typeKey.startsWith("stardewcraft.tool.")) {
            return StardewCatalogTab.MACHINES;
        }
        for (Map.Entry<StardewCatalogTab, List<String>> entry : TYPE_ORDER.entrySet()) {
            if (entry.getValue().contains(typeKey)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private static void auditFallbackItems() {
        if (auditedFallbackItems) {
            return;
        }
        auditedFallbackItems = true;

        List<String> fallbackIds = new ArrayList<>();
        for (Item item : visibleItems()) {
            if (!typeKey(item).isBlank() || item == ModItems.JUNIMO_NOTE.get()) {
                continue;
            }
            fallbackIds.add(id(item).toString());
        }
        if (!fallbackIds.isEmpty()) {
            int shown = Math.min(40, fallbackIds.size());
            StardewCraft.LOGGER.warn("Stardew item catalog sent {} untyped items to the Special tab: {}{}",
                    fallbackIds.size(),
                    String.join(", ", fallbackIds.subList(0, shown)),
                    fallbackIds.size() > shown ? ", ..." : "");
        }
    }

    private static ResourceLocation id(Item item) {
        return BuiltInRegistries.ITEM.getKey(item);
    }
}
