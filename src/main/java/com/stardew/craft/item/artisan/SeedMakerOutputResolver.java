package com.stardew.craft.item.artisan;

import com.stardew.craft.item.ModItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import javax.annotation.Nullable;

/** Shared deterministic part of seed-maker output resolution for runtime and JEI. */
public final class SeedMakerOutputResolver {
    private SeedMakerOutputResolver() {
    }

    @Nullable
    public static Item resolve(Item input) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(input);
        if ("stardewcraft".equals(id.getNamespace()) && "sweet_gem_berry".equals(id.getPath())) {
            return ModItems.RARE_SEED.get();
        }

        Item seasonal = seasonalWildSeedFor(id.getPath());
        if (seasonal != null) {
            return seasonal;
        }

        ResourceLocation seedId = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), id.getPath() + "_seeds");
        return BuiltInRegistries.ITEM.containsKey(seedId) ? BuiltInRegistries.ITEM.get(seedId) : null;
    }

    @Nullable
    private static Item seasonalWildSeedFor(String itemPath) {
        return switch (itemPath) {
            case "wild_horseradish", "daffodil", "leek", "dandelion" -> ModItems.SPRING_SEEDS.get();
            case "grape", "spice_berry", "sweet_pea" -> ModItems.SUMMER_SEEDS.get();
            case "wild_plum", "hazelnut", "blackberry", "common_mushroom" -> ModItems.FALL_SEEDS.get();
            case "winter_root", "crystal_fruit", "snow_yam", "crocus" -> ModItems.WINTER_SEEDS.get();
            default -> null;
        };
    }
}
