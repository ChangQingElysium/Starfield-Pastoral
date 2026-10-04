package com.stardew.craft.item.cooking;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.Item;
import com.stardew.craft.port.net.neoforged.neoforge.registries.DeferredItem;
import com.stardew.craft.port.net.neoforged.neoforge.registries.DeferredRegister;

public final class CookingDishRegistrar {
    private CookingDishRegistrar() {}

    public static Map<String, DeferredItem<Item>> registerAll(DeferredRegister.Items registry) {
        Map<String, DeferredItem<Item>> result = new LinkedHashMap<>();
        register(registry, "algae_soup", 100, 30, List.of(), result);
        register(registry, "artichoke_dip", 210, 40, List.of(), result);
        register(registry, "autumn_s_bounty", 350, 88, List.of(new CookingDishItem.DishBuff(CookingDishItem.BuffType.FORAGING, 2, 9240), new CookingDishItem.DishBuff(CookingDishItem.BuffType.DEFENSE, 2, 9240)), result);
        register(registry, "baked_fish", 100, 30, List.of(), result);
        register(registry, "banana_pudding", 260, 50, List.of(new CookingDishItem.DishBuff(CookingDishItem.BuffType.MINING, 1, 6020), new CookingDishItem.DishBuff(CookingDishItem.BuffType.LUCK, 1, 6020), new CookingDishItem.DishBuff(CookingDishItem.BuffType.DEFENSE, 1, 6020)), result);
        register(registry, "bean_hotpot", 100, 50, List.of(new CookingDishItem.DishBuff(CookingDishItem.BuffType.MAX_ENERGY, 30, 8400), new CookingDishItem.DishBuff(CookingDishItem.BuffType.MAGNETIC_RADIUS, 32, 8400)), result);
        register(registry, "blackberry_cobbler", 260, 70, List.of(), result);
        register(registry, "blueberry_tart", 150, 50, List.of(), result);
        register(registry, "bread", 60, 20, List.of(), result);
        register(registry, "bruschetta", 210, 45, List.of(), result);
        register(registry, "carp_surprise", 150, 36, List.of(), result);
        register(registry, "cheese_cauliflower", 300, 55, List.of(), result);
        register(registry, "chocolate_cake", 200, 60, List.of(), result);
        register(registry, "chowder", 135, 90, List.of(new CookingDishItem.DishBuff(CookingDishItem.BuffType.FISHING, 1, 20160)), result);
        register(registry, "coleslaw", 345, 85, List.of(), result);
        register(registry, "complete_breakfast", 350, 80, List.of(new CookingDishItem.DishBuff(CookingDishItem.BuffType.FARMING, 2, 8400), new CookingDishItem.DishBuff(CookingDishItem.BuffType.MAX_ENERGY, 50, 8400)), result);
        register(registry, "cookie", 140, 36, List.of(), result);
        register(registry, "crab_cakes", 275, 90, List.of(new CookingDishItem.DishBuff(CookingDishItem.BuffType.SPEED, 1, 20160), new CookingDishItem.DishBuff(CookingDishItem.BuffType.DEFENSE, 1, 20160)), result);
        register(registry, "cranberry_candy", 175, 50, List.of(), result);
        register(registry, "cranberry_sauce", 120, 50, List.of(new CookingDishItem.DishBuff(CookingDishItem.BuffType.MINING, 2, 4200)), result);
        register(registry, "crispy_bass", 150, 36, List.of(new CookingDishItem.DishBuff(CookingDishItem.BuffType.MAGNETIC_RADIUS, 64, 8400)), result);
        register(registry, "dish_o_the_sea", 220, 60, List.of(new CookingDishItem.DishBuff(CookingDishItem.BuffType.FISHING, 3, 6720)), result);
        register(registry, "eggplant_parmesan", 200, 70, List.of(new CookingDishItem.DishBuff(CookingDishItem.BuffType.MINING, 1, 5600), new CookingDishItem.DishBuff(CookingDishItem.BuffType.DEFENSE, 3, 5600)), result);
        register(registry, "escargot", 125, 90, List.of(new CookingDishItem.DishBuff(CookingDishItem.BuffType.FISHING, 2, 20160)), result);
        register(registry, "farmer_s_lunch", 150, 80, List.of(new CookingDishItem.DishBuff(CookingDishItem.BuffType.FARMING, 3, 6720)), result);
        register(registry, "fiddlehead_risotto", 350, 90, List.of(), result);
        register(registry, "fish_stew", 175, 90, List.of(new CookingDishItem.DishBuff(CookingDishItem.BuffType.FISHING, 3, 20160)), result);
        register(registry, "fish_taco", 500, 66, List.of(new CookingDishItem.DishBuff(CookingDishItem.BuffType.FISHING, 2, 8400)), result);
        register(registry, "fried_calamari", 150, 32, List.of(), result);
        register(registry, "fried_chicken_fries", 260, 70, List.of(new CookingDishItem.DishBuff(CookingDishItem.BuffType.ATTACK, 1, 5600)), result);
        register(registry, "fried_eel", 120, 30, List.of(new CookingDishItem.DishBuff(CookingDishItem.BuffType.LUCK, 1, 8400)), result);
        register(registry, "fried_egg", 35, 20, List.of(), result);
        register(registry, "fried_mushroom", 200, 54, List.of(new CookingDishItem.DishBuff(CookingDishItem.BuffType.ATTACK, 2, 8400)), result);
        register(registry, "fruit_salad", 450, 105, List.of(), result);
        registerDrink(registry, "ginger_ale", 200, 25, List.of(new CookingDishItem.DishBuff(CookingDishItem.BuffType.LUCK, 1, 6020)), result);
        register(registry, "glazed_yams", 200, 80, List.of(), result);
        register(registry, "hashbrowns", 120, 36, List.of(new CookingDishItem.DishBuff(CookingDishItem.BuffType.FARMING, 1, 6720)), result);
        register(registry, "ice_cream", 120, 40, List.of(), result);
        register(registry, "lobster_bisque", 205, 90, List.of(new CookingDishItem.DishBuff(CookingDishItem.BuffType.FISHING, 3, 20160), new CookingDishItem.DishBuff(CookingDishItem.BuffType.MAX_ENERGY, 50, 20160)), result);
        register(registry, "lucky_lunch", 250, 40, List.of(new CookingDishItem.DishBuff(CookingDishItem.BuffType.LUCK, 3, 13440)), result);
        register(registry, "maki_roll", 220, 40, List.of(), result);
        register(registry, "mango_sticky_rice", 250, 45, List.of(new CookingDishItem.DishBuff(CookingDishItem.BuffType.DEFENSE, 3, 6020)), result);
        register(registry, "maple_bar", 300, 90, List.of(new CookingDishItem.DishBuff(CookingDishItem.BuffType.FARMING, 1, 20160), new CookingDishItem.DishBuff(CookingDishItem.BuffType.FISHING, 1, 20160), new CookingDishItem.DishBuff(CookingDishItem.BuffType.MINING, 1, 20160)), result);
        register(registry, "miner_s_treat", 200, 50, List.of(new CookingDishItem.DishBuff(CookingDishItem.BuffType.MINING, 3, 6720), new CookingDishItem.DishBuff(CookingDishItem.BuffType.MAGNETIC_RADIUS, 32, 6720)), result);
        register(registry, "moss_soup", 80, 28, List.of(), result);
        register(registry, "omelet", 125, 40, List.of(), result);
        register(registry, "pale_broth", 150, 50, List.of(), result);
        register(registry, "pancakes", 80, 36, List.of(new CookingDishItem.DishBuff(CookingDishItem.BuffType.FORAGING, 2, 13440)), result);
        register(registry, "parsnip_soup", 120, 34, List.of(), result);
        register(registry, "pepper_poppers", 200, 52, List.of(new CookingDishItem.DishBuff(CookingDishItem.BuffType.FARMING, 2, 8400), new CookingDishItem.DishBuff(CookingDishItem.BuffType.SPEED, 1, 8400)), result);
        register(registry, "pink_cake", 480, 100, List.of(), result);
        register(registry, "pizza", 300, 60, List.of(), result);
        register(registry, "plum_pudding", 260, 70, List.of(), result);
        register(registry, "poi", 400, 30, List.of(), result);
        register(registry, "poppyseed_muffin", 250, 60, List.of(), result);
        register(registry, "popsicle", 90, 28, List.of(), result);
        register(registry, "pumpkin_pie", 385, 90, List.of(), result);
        register(registry, "pumpkin_soup", 300, 80, List.of(new CookingDishItem.DishBuff(CookingDishItem.BuffType.LUCK, 2, 9240), new CookingDishItem.DishBuff(CookingDishItem.BuffType.DEFENSE, 2, 9240)), result);
        register(registry, "radish_salad", 300, 80, List.of(), result);
        register(registry, "red_plate", 400, 96, List.of(new CookingDishItem.DishBuff(CookingDishItem.BuffType.MAX_ENERGY, 50, 4200)), result);
        register(registry, "rhubarb_pie", 400, 86, List.of(), result);
        register(registry, "rice_pudding", 260, 46, List.of(), result);
        register(registry, "roasted_hazelnuts", 270, 70, List.of(), result);
        register(registry, "roots_platter", 100, 50, List.of(new CookingDishItem.DishBuff(CookingDishItem.BuffType.ATTACK, 3, 6720)), result);
        register(registry, "salad", 110, 45, List.of(), result);
        register(registry, "salmon_dinner", 300, 50, List.of(), result);
        register(registry, "sashimi", 75, 30, List.of(), result);
        register(registry, "seafoam_pudding", 300, 70, List.of(new CookingDishItem.DishBuff(CookingDishItem.BuffType.FISHING, 4, 4200)), result);
        register(registry, "shrimp_cocktail", 160, 90, List.of(new CookingDishItem.DishBuff(CookingDishItem.BuffType.FISHING, 1, 12040), new CookingDishItem.DishBuff(CookingDishItem.BuffType.LUCK, 1, 12040)), result);
        register(registry, "spaghetti", 120, 30, List.of(), result);
        register(registry, "spicy_eel", 175, 46, List.of(new CookingDishItem.DishBuff(CookingDishItem.BuffType.LUCK, 1, 8400), new CookingDishItem.DishBuff(CookingDishItem.BuffType.SPEED, 1, 8400)), result);
        register(registry, "squid_ink_ravioli", 150, 50, List.of(new CookingDishItem.DishBuff(CookingDishItem.BuffType.MINING, 1, 5600)), result);
        register(registry, "stir_fry", 335, 80, List.of(), result);
        register(registry, "strange_bun", 225, 40, List.of(), result);
        register(registry, "stuffing", 165, 68, List.of(new CookingDishItem.DishBuff(CookingDishItem.BuffType.DEFENSE, 2, 6720)), result);
        register(registry, "super_meal", 220, 64, List.of(new CookingDishItem.DishBuff(CookingDishItem.BuffType.MAX_ENERGY, 40, 4200), new CookingDishItem.DishBuff(CookingDishItem.BuffType.SPEED, 1, 4200)), result);
        register(registry, "survival_burger", 180, 50, List.of(new CookingDishItem.DishBuff(CookingDishItem.BuffType.FORAGING, 3, 6720)), result);
        register(registry, "tom_kha_soup", 250, 70, List.of(new CookingDishItem.DishBuff(CookingDishItem.BuffType.FARMING, 2, 8400), new CookingDishItem.DishBuff(CookingDishItem.BuffType.MAX_ENERGY, 30, 8400)), result);
        register(registry, "tortilla", 50, 20, List.of(), result);
        registerDrink(registry, "triple_shot_espresso", 450, 3, List.of(new CookingDishItem.DishBuff(CookingDishItem.BuffType.SPEED, 1, 5040)), result);
        register(registry, "tropical_curry", 500, 60, List.of(new CookingDishItem.DishBuff(CookingDishItem.BuffType.FORAGING, 4, 6020)), result);
        register(registry, "trout_soup", 100, 40, List.of(new CookingDishItem.DishBuff(CookingDishItem.BuffType.FISHING, 1, 5600)), result);
        register(registry, "vegetable_medley", 120, 66, List.of(), result);
        return result;
    }

    private static void register(
            DeferredRegister.Items registry,
            String id,
            int sellPrice,
            int edibility,
            List<CookingDishItem.DishBuff> buffs,
            Map<String, DeferredItem<Item>> out) {
        register(registry, id, sellPrice, edibility, buffs, false, out);
    }

    private static void registerDrink(
            DeferredRegister.Items registry,
            String id,
            int sellPrice,
            int edibility,
            List<CookingDishItem.DishBuff> buffs,
            Map<String, DeferredItem<Item>> out) {
        register(registry, id, sellPrice, edibility, buffs, true, out);
    }

    private static void register(
            DeferredRegister.Items registry,
            String id,
            int sellPrice,
            int edibility,
            List<CookingDishItem.DishBuff> buffs,
            boolean drinkAnimation,
            Map<String, DeferredItem<Item>> out) {
        @SuppressWarnings("null")
        DeferredItem<Item> item = registry.register(id, () -> new CookingDishItem(
                sellPrice,
                edibility,
                buffs,
                new Item.Properties().stacksTo(999),
                drinkAnimation
        ));
        out.put(id, item);
    }
}
