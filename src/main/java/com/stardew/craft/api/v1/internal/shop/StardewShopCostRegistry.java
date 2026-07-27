package com.stardew.craft.api.v1.internal.shop;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.economy.StardewCost;
import com.stardew.craft.api.v1.internal.extension.OrderedExtensionRegistry;
import com.stardew.craft.api.v1.shop.StardewShopCostContext;
import com.stardew.craft.api.v1.shop.StardewShopCostProvider;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Ordered, failure-isolated standard-shop cost transformation. */
public final class StardewShopCostRegistry {
    private static final OrderedExtensionRegistry<
            StardewShopCostProvider> REGISTRY =
            new OrderedExtensionRegistry<>(
                    ResourceLocation.fromNamespaceAndPath(
                            StardewCraft.MODID, "shop_costs"));

    private StardewShopCostRegistry() {
    }

    public static void register(
            ResourceLocation id,
            int priority,
            StardewShopCostProvider provider
    ) {
        REGISTRY.register(
                id, priority,
                Objects.requireNonNull(provider, "provider"));
    }

    public static Resolution resolve(
            StardewShopCostContext context,
            StardewCost defaultCost
    ) {
        Objects.requireNonNull(context, "context");
        StardewCost resolved =
                Objects.requireNonNull(defaultCost, "defaultCost");
        boolean modified = false;
        for (var entry : REGISTRY.entries()) {
            try {
                StardewCost proposedCost = resolved;
                StardewCost candidate = REGISTRY.invoke(
                        entry,
                        provider -> provider.resolve(
                                context, proposedCost));
                if (candidate == null) {
                    StardewCraft.LOGGER.error(
                            "Shop cost provider {} returned null "
                                    + "for {} / {}",
                            entry.id(), context.shopId(),
                            context.entry().item());
                    continue;
                }
                modified |= !candidate.equals(resolved);
                resolved = candidate;
            } catch (RuntimeException exception) {
                StardewCraft.LOGGER.error(
                        "Shop cost provider {} failed for {} / {}",
                        entry.id(), context.shopId(),
                        context.entry().item(), exception);
            }
        }
        return new Resolution(resolved, modified);
    }

    public record Resolution(
            StardewCost cost,
            boolean modified
    ) {
    }
}
