package com.stardew.craft.item.artisan;

import net.minecraft.resources.ResourceLocation;

import java.util.Set;

public final class PreservesCropTypeHelper {
    private static final Set<String> FRUIT_CROPS = Set.of(
            "ancient_fruit",
            "blueberry",
            "cranberry",
            "grape",
            "hot_pepper",
            "melon",
            "powdermelon",
            "rhubarb",
            "starfruit",
            "strawberry"
    );

    private static final Set<String> VEGETABLE_CROPS = Set.of(
            "amaranth",
            "artichoke",
            "beet",
            "bok_choy",
            "broccoli",
            "carrot",
            "cauliflower",
            "corn",
            "eggplant",
            "garlic",
            "green_bean",
            "hops",
            "kale",
            "parsnip",
            "potato",
            "pumpkin",
            "radish",
            "red_cabbage",
            "tomato",
            "wheat",
            "yam"
    );

    private PreservesCropTypeHelper() {
    }

    public static PreserveType getCropPreserveType(ResourceLocation id) {
        if (id == null) {
            return null;
        }
        String key = id.getPath();
        if (FRUIT_CROPS.contains(key)) {
            return PreserveType.JELLY;
        }
        if (VEGETABLE_CROPS.contains(key)) {
            return PreserveType.PICKLES;
        }
        return null;
    }
}
