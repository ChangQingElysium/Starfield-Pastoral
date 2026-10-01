package com.stardew.craft.item;

import net.minecraft.world.item.Item;
import com.stardew.craft.port.net.neoforged.neoforge.registries.DeferredItem;
import net.minecraftforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registers vanilla-aligned fruit/forage/cooking-ingredient objects that are missing in current mod scope.
 */
public final class VanillaCategoryItemRegistrar {
    private record Entry(String id, String typeKey, int price, int edibility, boolean supportsQuality) {}

    private static final List<Entry> ENTRIES = List.of(
        new Entry("green_slime_egg", "stardewcraft.type.resource", 1000, -300, false),
        new Entry("tiger_slime_egg", "stardewcraft.type.resource", 8000, -300, false),
        new Entry("golden_coconut", "stardewcraft.type.resource", 0, -300, false),
        new Entry("cactus_seeds", "stardewcraft.type.seed", 0, -300, false),
        new Entry("taro_tuber", "stardewcraft.type.seed", 20, -300, false),
        new Entry("pineapple_seeds", "stardewcraft.type.seed", 240, -300, false),
        new Entry("lumber", "stardewcraft.type.resource", 2, 10, false),
        new Entry("wild_horseradish", "stardewcraft.type.forage", 50, 5, true),
        new Entry("daffodil", "stardewcraft.type.forage", 30, 0, true),
        new Entry("leek", "stardewcraft.type.forage", 60, 16, true),
        new Entry("dandelion", "stardewcraft.type.forage", 40, 10, true),
        new Entry("cave_carrot", "stardewcraft.type.forage", 25, 12, true),
        new Entry("coconut", "stardewcraft.type.forage", 100, -300, true),
        new Entry("cactus_fruit", "stardewcraft.type.forage", 75, 30, true),
        new Entry("banana", "stardewcraft.type.fruit", 150, 30, true),
        new Entry("sugar", "stardewcraft.type.cooking_ingredient", 50, 10, false),
        new Entry("wheat_flour", "stardewcraft.type.cooking_ingredient", 50, 5, false),
        new Entry("fiddlehead_fern", "stardewcraft.type.forage", 90, 10, true),
        new Entry("cranberries", "stardewcraft.type.cooking_ingredient", 75, 15, false),
        new Entry("holly", "stardewcraft.type.forage", 80, -15, true),
        new Entry("nautilus_shell", "stardewcraft.type.forage", 120, -300, true),
        new Entry("coral", "stardewcraft.type.forage", 80, -300, true),
        new Entry("rainbow_shell", "stardewcraft.type.forage", 300, -300, true),
        new Entry("spice_berry", "stardewcraft.type.forage", 80, 10, true),
        new Entry("sea_urchin", "stardewcraft.type.forage", 160, -300, true),
        new Entry("spring_onion", "stardewcraft.type.forage", 8, 5, true),
        new Entry("sweet_pea", "stardewcraft.type.forage", 50, 0, true),
        new Entry("wild_plum", "stardewcraft.type.forage", 80, 10, true),
        new Entry("hazelnut", "stardewcraft.type.forage", 90, 12, true),
        new Entry("blackberry", "stardewcraft.type.forage", 20, 10, true),
        new Entry("winter_root", "stardewcraft.type.forage", 70, 10, true),
        new Entry("snow_yam", "stardewcraft.type.forage", 100, 12, true),
        new Entry("crystal_fruit", "stardewcraft.type.forage", 150, 25, true),
        new Entry("crocus", "stardewcraft.type.forage", 60, 0, true),
        new Entry("vinegar", "stardewcraft.type.cooking_ingredient", 100, 5, false),
        new Entry("rice", "stardewcraft.type.cooking_ingredient", 100, 5, false),
        new Entry("unmilled_rice", "stardewcraft.type.crop", 30, 1, true),
        new Entry("apple", "stardewcraft.type.fruit", 100, 15, true),
        new Entry("apricot", "stardewcraft.type.fruit", 50, 15, true),
        new Entry("orange", "stardewcraft.type.fruit", 100, 15, true),
        new Entry("peach", "stardewcraft.type.fruit", 140, 15, true),
        new Entry("pomegranate", "stardewcraft.type.fruit", 140, 15, true),
        new Entry("cherry", "stardewcraft.type.fruit", 80, 15, true),
        new Entry("squid_ink", "stardewcraft.type.cooking_ingredient", 110, -300, false),
        new Entry("ginger", "stardewcraft.type.forage", 60, 10, true),
        new Entry("taro_root", "stardewcraft.type.cooking_ingredient", 100, 15, true),
        new Entry("pineapple", "stardewcraft.type.cooking_ingredient", 300, 55, true),
        new Entry("mango", "stardewcraft.type.fruit", 130, 40, true),
        new Entry("qi_fruit", "stardewcraft.type.crop", 1, 1, true),
        new Entry("qi_bean", "stardewcraft.type.seed", 1, -300, false),
        new Entry("salmonberry", "stardewcraft.type.forage", 5, 10, true),
        new Entry("dragon_tooth", "stardewcraft.type.resource", 500, -300, false)
    );

    private VanillaCategoryItemRegistrar() {
    }

    @SuppressWarnings("null")
    public static Map<String, DeferredItem<Item>> registerAll(DeferredRegister.Items items) {
        Map<String, DeferredItem<Item>> out = new LinkedHashMap<>();
        for (Entry entry : ENTRIES) {
            DeferredItem<Item> reg = items.register(entry.id(),
                    () -> entry.id().equals("taro_tuber")
                            ? new com.stardew.craft.item.crop.summer.TaroTuberItem(new Item.Properties().stacksTo(999))
                            : entry.id().equals("pineapple_seeds")
                            ? new com.stardew.craft.item.crop.summer.PineappleSeedItem(new Item.Properties().stacksTo(999))
                            : entry.id().equals("cactus_seeds")
                            ? new com.stardew.craft.item.crop.CactusSeedItem(new Item.Properties().stacksTo(999))
                            : entry.id().equals("qi_bean")
                            ? new com.stardew.craft.item.crop.QiBeanItem(new Item.Properties().stacksTo(999))
                            : new StardewQualityItem(entry.typeKey(), entry.price(), entry.edibility(), entry.supportsQuality(),
                            new Item.Properties().stacksTo(999)));
            out.put(entry.id(), reg);
        }
        return out;
    }
}
