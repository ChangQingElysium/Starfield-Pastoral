package com.stardew.craft.api.v1.item;

import com.stardew.craft.data.StardewDataMaps;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/** Stable read-only entry point for food effects supplied by data packs or addons. */
public final class StardewFoodEffects {
    private StardewFoodEffects() {
    }

    public static Optional<StardewFoodEffectData> resolve(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(BuiltInRegistries.ITEM.wrapAsHolder(stack.getItem())
                        .getData(StardewDataMaps.FOOD_EFFECTS))
                .filter(data -> !data.effects().isEmpty());
    }
}
