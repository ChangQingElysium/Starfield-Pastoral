package com.stardew.craft.item.cooking;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.Item;
import com.stardew.craft.port.net.neoforged.neoforge.registries.DeferredItem;
import net.minecraftforge.registries.DeferredRegister;

public final class DesertFestivalCookDishRegistrar {
    private static final String TYPE_KEY = "stardewcraft.type.festival_food";
    private static final int DURATION = 600 * 20;
    private static final int SELL_PRICE = 0;
    private static final int EDIBILITY = 100;

    private DesertFestivalCookDishRegistrar() {
    }

    public static Map<String, DeferredItem<Item>> registerAll(DeferredRegister.Items registry) {
        Map<String, DeferredItem<Item>> result = new LinkedHashMap<>();
        register(registry, result, "earthy_mousse", Buff.defense(3), Buff.mining(1));
        register(registry, result, "sweet_bean_cake", Buff.defense(3), Buff.luck(1));
        register(registry, result, "skull_cave_casserole", Buff.defense(3), Buff.attack(1));
        register(registry, result, "spicy_tacos", Buff.defense(3), Buff.speed(1));
        register(registry, result, "mountain_chili", Buff.mining(3), Buff.defense(1));
        register(registry, result, "crystal_cake", Buff.mining(3), Buff.luck(1));
        register(registry, result, "cave_kebab", Buff.mining(3), Buff.attack(1));
        register(registry, result, "hot_log", Buff.mining(3), Buff.speed(1));
        register(registry, result, "sour_salad", Buff.luck(3), Buff.defense(1));
        register(registry, result, "superfood_cake", Buff.luck(3), Buff.mining(1));
        register(registry, result, "warrior_smoothie", Buff.luck(3), Buff.attack(1));
        register(registry, result, "rumpled_fruit_skin", Buff.luck(3), Buff.speed(1));
        register(registry, result, "calico_pizza", Buff.attack(3), Buff.defense(1));
        register(registry, result, "stuffed_mushrooms_desert", Buff.attack(3), Buff.mining(1));
        register(registry, result, "elf_quesadilla", Buff.attack(3), Buff.luck(1));
        register(registry, result, "nachos_of_the_desert", Buff.attack(3), Buff.speed(1));
        register(registry, result, "cioppino_desert", Buff.fishing(3), Buff.defense(1));
        register(registry, result, "rainforest_shrimp", Buff.fishing(3), Buff.mining(1));
        register(registry, result, "shrimp_donut", Buff.fishing(3), Buff.luck(1));
        register(registry, result, "smell_of_the_sea", Buff.fishing(3), Buff.attack(1));
        register(registry, result, "desert_gumbo", Buff.fishing(3), Buff.speed(1));
        return result;
    }

    private static void register(DeferredRegister.Items registry, Map<String, DeferredItem<Item>> out,
                                 String id, Buff primary, Buff sauce) {
        DeferredItem<Item> item = registry.register(id, () -> new CookingDishItem(
            TYPE_KEY,
            SELL_PRICE,
            EDIBILITY,
            List.of(primary.toDishBuff(), sauce.toDishBuff()),
            new Item.Properties().stacksTo(999)
        ));
        out.put(id, item);
    }

    private record Buff(CookingDishItem.BuffType type, int amount) {
        private CookingDishItem.DishBuff toDishBuff() {
            return new CookingDishItem.DishBuff(type, amount, DURATION);
        }

        private static Buff defense(int amount) {
            return new Buff(CookingDishItem.BuffType.DEFENSE, amount);
        }

        private static Buff mining(int amount) {
            return new Buff(CookingDishItem.BuffType.MINING, amount);
        }

        private static Buff luck(int amount) {
            return new Buff(CookingDishItem.BuffType.LUCK, amount);
        }

        private static Buff attack(int amount) {
            return new Buff(CookingDishItem.BuffType.ATTACK, amount);
        }

        private static Buff fishing(int amount) {
            return new Buff(CookingDishItem.BuffType.FISHING, amount);
        }

        private static Buff speed(int amount) {
            return new Buff(CookingDishItem.BuffType.SPEED, amount);
        }
    }
}