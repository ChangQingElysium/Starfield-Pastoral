package com.stardew.craft.item.catalog;

import com.stardew.craft.fishing.data.SpawnFishRule;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class StardewItemComparator {
    // Authoring workflow order, independent of translated names or registration order.
    private static final List<String> BUILDING_ORDER = List.of(
            "pale_blue_siding", "blue_painted_planks", "blue_gray_timber", "teal_painted_timber", "pale_cyan_plaster", "cream_siding", "ivory_siding", "gray_violet_roof_tiles", "brick_red_roof_tiles", "terracotta_roof_tiles", "dark_brown_roof_tiles", "gray_green_masonry", "gray_brown_bricks", "chestnut_roof_tiles", "forest_green_siding", "gray_metal_siding",
            "green_panel_door", "red_glass_door", "brown_glass_door", "red_brown_door", "blue_glass_door", "shop_glass_door", "pale_blue_window_glass", "owl_pendant", "hanging_basket", "ship_wheel_ornament", "sun_wall_ornament", "pierre_sign", "clinic_sign", "saloon_sign", "food_spirits_sign", "blacksmith_door", "purple_panel_door", "library_book_sign", "wall_magnifying_glass", "timber_awning",
            "town_paving", "town_paving_stairs", "plaza_red_bricks",
            "wood_floor", "weathered_floor", "rustic_plank_floor", "wood_path",
            "stone_floor", "stone_walkway_floor", "cobblestone_path", "stepping_stone_path", "gravel_path",
            "brick_floor", "crystal_floor", "crystal_path", "straw_floor",
            "asphalt_road", "road_dash", "road_double_line", "manhole", "road_sign", "leaning_shovel", "old_plank",
            "rural_fence", "wood_fence", "hardwood_fence",
            "garden_planter", "park_bench", "outdoor_table", "park_fountain",
            "playground_sand", "playground_slide", "climbing_frame", "double_swing", "bird_spring_rider", "old_tire", "dog_house",
            "ice_cream_stand", "plaza_display", "blacksmith_ventilator", "bookseller_stall", "bookseller_balloon",
            "ticket_machine", "bus", "mayor_pickup", "joja_truck", "joja_billboard", "joja_supermarket_crate", "shopping_basket",
            "short_gravestone", "tall_gravestone",
            "mine_planks", "mine_masonry", "mine_step_stone", "mine_timber_support", "mine_blocked_entry",
            "mine_exit", "mine_ladder", "mine_rail", "mine_rail_curve", "minecart_power_unit",
            "empty_minecart", "coal_minecart", "mine_coal_backpack", "mine_chest", "golden_small_chest",
            "mine_serpent_pillar", "mine_spiral_column", "mine_desert_wall_relief",
            "skull_cavern_door", "skull_shrine_wall", "skull_shrine_altar", "skull_wall_brazier", "skull_stalagmite"
    );
    private static final List<String> NATURE_ORDER = List.of(
            "dirt", "dirt_slab", "dirt_stairs", "grass_block", "grass_slab", "grass_stairs", "dark_grass_block", "dark_grass_slab", "dark_grass_stairs", "farmland", "cliff", "cliff_slab", "cliff_stairs",
            "mine_earth_soil", "mine_earth_loose_soil", "mine_earth_wall",
            "mine_earth_dark_soil", "mine_earth_dark_loose_soil", "mine_earth_dark_wall",
            "mine_frost_soil", "mine_frost_loose_soil", "mine_frost_wall",
            "mine_frost_dark_soil", "mine_frost_dark_loose_soil", "mine_frost_dark_wall",
            "mine_lava_soil", "mine_lava_loose_soil", "mine_lava_wall",
            "mine_lava_dark_soil", "mine_lava_dark_loose_soil", "mine_lava_dark_wall",
            "mine_desert_soil", "mine_desert_loose_soil", "mine_desert_wall",
            "mine_desert_dark_soil", "mine_desert_dark_loose_soil", "mine_desert_dark_wall",
            "mine_stone_32", "mine_stone_38", "mine_stone_40", "mine_stone_42", "mine_stone_668", "mine_stone_670", "mine_stone_751", "mine_stone_8", "mine_stone_10", "mine_stone_44", "mine_stone_34", "mine_stone_36", "mine_rock_clump_752", "mine_rock_clump_754", "mine_stone_48", "mine_stone_50", "mine_stone_52", "mine_stone_54", "mine_stone_290", "mine_stone_6", "mine_stone_14", "mine_stone_2", "mine_rock_clump_756", "mine_rock_clump_758", "mine_rock_clump_672", "mine_rock_clump_622", "mine_rock_clump_148", "mine_stone_56", "mine_stone_58", "mine_stone_760", "mine_stone_762", "mine_stone_764", "mine_stone_4", "mine_stone_12", "mine_stone_46", "mine_stone_765", "mine_stone_calico_egg_stone_0", "mine_stone_calico_egg_stone_1", "mine_stone_calico_egg_stone_2", "mine_stone_75", "mine_stone_76", "mine_stone_77", "mine_stone_95", "mine_stone_343", "mine_stone_450", "mine_stone_25", "mine_stone_816", "mine_stone_817", "mine_stone_818", "mine_stone_819", "mine_stone_843", "mine_stone_844", "mine_stone_845", "mine_stone_846", "mine_stone_847", "mine_stone_849", "mine_stone_850", "mine_stone_volcano_gold_node", "mine_stone_volcano_coal_node0", "mine_stone_volcano_coal_node1", "mine_stone_basic_coal_node0", "mine_stone_basic_coal_node1", "frost_wall_ice", "desert_wall_crust", "mine_desert_wall_flakes",
            "wild_grass_clump", "broadleaf_clump", "mine_earth_weeds", "mine_lava_weeds", "mine_vines", "mine_canopy", "lava_mine_vines",
            "wildflower_cluster_a", "wildflower_cluster_b", "low_flower_clump", "seasonal_ground_sprig",
            "floating_leaf", "water_lily", "aquatic_grass_clump", "forest_leaves", "broadleaf_leaves"
    );
    private static final List<String> MINE_THEME_ORDER = List.of(
            "earth", "earth_dark", "frost", "frost_dark", "lava", "lava_dark", "desert", "desert_dark");

    private static final Map<String, String> PATH_SORT_OVERRIDES = Map.ofEntries(
            Map.entry("dirt", "00_ground_0_dirt"),
            Map.entry("grass_block", "00_ground_1_grass"),
            Map.entry("dark_grass_block", "00_ground_2_dark_grass"),
            Map.entry("farmland", "00_ground_3_farmland"),
            Map.entry("cliff", "00_rock_cliff"),
            Map.entry("wild_grass_clump", "00_foliage_0_grass"),
            Map.entry("broadleaf_clump", "00_foliage_1_broadleaf"),
            Map.entry("wildflower_cluster_a", "00_flower_0_wildflower"),
            Map.entry("wildflower_cluster_b", "00_flower_1_wildflower"),
            Map.entry("low_flower_clump", "00_flower_2_low"),
            Map.entry("seasonal_ground_sprig", "00_flower_3_sprig"),
            Map.entry("floating_leaf", "00_aquatic_0_leaf"),
            Map.entry("water_lily", "00_aquatic_1_lily"),
            Map.entry("aquatic_grass_clump", "00_aquatic_2_grass"),
            Map.entry("dresser_1", "dresser_1_00_birch_bedside_cabinet"),
            Map.entry("redwood_bedside_cabinet", "dresser_1_01_redwood_bedside_cabinet"),
            Map.entry("walnut_bedside_cabinet", "dresser_1_02_walnut_bedside_cabinet"),
            Map.entry("oak_bedside_cabinet", "dresser_1_03_oak_bedside_cabinet"),
            Map.entry("dresser_2", "dresser_1_10_birch_dresser"),
            Map.entry("redwood_dresser", "dresser_1_11_redwood_dresser"),
            Map.entry("walnut_dresser", "dresser_1_12_walnut_dresser"),
            Map.entry("oak_dresser", "dresser_1_13_oak_dresser"),
            Map.entry("dresser_3", "dresser_1_20_birch_wardrobe"),
            Map.entry("redwood_wardrobe", "dresser_1_21_redwood_wardrobe"),
            Map.entry("walnut_wardrobe", "dresser_1_22_walnut_wardrobe"),
            Map.entry("oak_wardrobe", "dresser_1_23_oak_wardrobe")
    );

    private StardewItemComparator() {
    }

    public static final Comparator<Item> ITEM = StardewItemComparator::compareItems;
    public static final Comparator<ItemStack> STACK = StardewItemComparator::compareStacks;
    public static final Comparator<SpawnFishRule> FISH_RULE = StardewItemComparator::compareFishRules;

    public static int compareItems(Item left, Item right) {
        if (left == right) {
            return 0;
        }
        StardewCatalogTab leftTab = StardewItemCatalog.tabForItem(left);
        StardewCatalogTab rightTab = StardewItemCatalog.tabForItem(right);
        int tabCompare = Integer.compare(leftTab.ordinal(), rightTab.ordinal());
        if (tabCompare != 0) {
            return tabCompare;
        }

        if (leftTab == StardewCatalogTab.BUILDING || leftTab == StardewCatalogTab.NATURE) {
            List<String> order = leftTab == StardewCatalogTab.BUILDING ? BUILDING_ORDER : NATURE_ORDER;
            int authored = Integer.compare(orderIndex(order, path(left)), orderIndex(order, path(right)));
            if (authored != 0) return authored;
            // New entries remain visible after the authored groups until deliberately placed.
            return sortPath(left).compareTo(sortPath(right));
        }

        int sectionCompare = Integer.compare(
                StardewItemCatalog.sectionOrder(leftTab, left),
                StardewItemCatalog.sectionOrder(rightTab, right));
        if (sectionCompare != 0) {
            return sectionCompare;
        }

        return sortPath(left).compareTo(sortPath(right));
    }

    public static int compareStacks(ItemStack left, ItemStack right) {
        int itemCompare = compareItems(left.getItem(), right.getItem());
        if (itemCompare != 0) {
            return itemCompare;
        }
        var tab = StardewItemCatalog.tabForItem(left.getItem());
        if (tab == StardewCatalogTab.BUILDING || tab == StardewCatalogTab.NATURE) {
            int themeCompare = Integer.compare(themeOrder(left), themeOrder(right));
            if (themeCompare != 0) return themeCompare;
        }
        return Integer.compare(variantOrder(left), variantOrder(right));
    }

    private static int orderIndex(List<String> order, String value) {
        int index = order.indexOf(value);
        return index < 0 ? order.size() : index;
    }

    private static int themeOrder(ItemStack stack) {
        var state = stack.get(net.minecraft.core.component.DataComponents.BLOCK_STATE);
        return state == null ? -1 : orderIndex(MINE_THEME_ORDER, state.properties().getOrDefault("theme", ""));
    }

    public static int compareFishRules(SpawnFishRule left, SpawnFishRule right) {
        Item leftItem = itemFromId(left.itemId());
        Item rightItem = itemFromId(right.itemId());
        int itemCompare = compareItems(leftItem, rightItem);
        if (itemCompare != 0) {
            return itemCompare;
        }
        return left.itemId().compareTo(right.itemId());
    }

    public static String path(Item item) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        return id == null ? "" : id.getPath();
    }

    private static String sortPath(Item item) {
        String path = path(item);
        return PATH_SORT_OVERRIDES.getOrDefault(path, path);
    }

    private static Item itemFromId(String rawId) {
        try {
            return BuiltInRegistries.ITEM.get(ResourceLocation.parse(rawId));
        } catch (Exception ignored) {
            return net.minecraft.world.item.Items.AIR;
        }
    }

    private static int variantOrder(ItemStack stack) {
        int quality = com.stardew.craft.item.quality.QualityHelper.getQuality(stack);
        Integer flowerColor = StardewItemDisplayStacks.getFlowerColor(stack);
        if (flowerColor != null) {
            return flowerColor * 10 + quality;
        }
        return quality;
    }
}
