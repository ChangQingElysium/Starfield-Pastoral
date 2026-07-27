package com.stardew.craft.api.v1.shop;

import com.stardew.craft.api.v1.economy.StardewCost;
import com.stardew.craft.api.v1.economy.StardewCosts;
import com.stardew.craft.api.v1.internal.shop.StardewShopCostRegistry;
import com.stardew.craft.api.v1.requirement.StardewRequirementReport;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Registration and resolution facade for custom standard-shop costs. */
public final class StardewShopCosts {
    private StardewShopCosts() {
    }

    public static void register(
            ResourceLocation id,
            int priority,
            StardewShopCostProvider provider
    ) {
        StardewShopCostRegistry.register(
                id, priority, provider);
    }

    public static StardewCost resolve(
            StardewShopCostContext context,
            StardewCost defaultCost
    ) {
        return StardewShopCostRegistry.resolve(
                context, defaultCost).cost();
    }

    /**
     * Resolves addon cost providers and explains current affordability without
     * performing a purchase.
     */
    public static StardewRequirementReport requirements(
            StardewShopCostContext context,
            StardewCost defaultCost
    ) {
        Objects.requireNonNull(context, "context");
        StardewCost resolved = resolve(
                context,
                Objects.requireNonNull(defaultCost, "defaultCost"));
        return StardewCosts.requirements(
                context.player(), resolved);
    }
}
