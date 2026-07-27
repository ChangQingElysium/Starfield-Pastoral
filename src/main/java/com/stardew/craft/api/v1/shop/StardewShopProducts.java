package com.stardew.craft.api.v1.shop;

import com.stardew.craft.api.v1.internal.shop.StardewShopProductRegistry;
import com.stardew.craft.api.v1.requirement.StardewRequirementReport;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Registration facade for ordered non-item shop product handlers. */
public final class StardewShopProducts {
    private StardewShopProducts() {
    }

    public static void register(
            ResourceLocation id,
            int priority,
            StardewShopProductHandler handler
    ) {
        StardewShopProductRegistry.register(
                id, priority, handler);
    }

    /**
     * Registers a lambda-friendly product handler with a separate explanation
     * provider for accepted or rejected preparations.
     */
    public static void register(
            ResourceLocation id,
            int priority,
            StardewShopProductHandler handler,
            StardewShopProductRequirementProvider requirements
    ) {
        Objects.requireNonNull(handler, "handler");
        Objects.requireNonNull(requirements, "requirements");
        StardewShopProductRegistry.register(
                id,
                priority,
                new StardewShopProductHandler() {
                    @Override
                    public StardewShopProductPreparation prepare(
                            StardewShopProductContext context
                    ) {
                        return handler.prepare(context);
                    }

                    @Override
                    public StardewRequirementReport requirements(
                            StardewShopProductContext context,
                            StardewShopProductDecision decision
                    ) {
                        return requirements.requirements(
                                context, decision);
                    }
                });
    }

    /**
     * Runs the same ordered pre-payment resolution as a purchase and returns
     * its read-only explanation. No payment, stock mutation or grant occurs.
     */
    public static StardewRequirementReport requirements(
            StardewShopProductContext context
    ) {
        return StardewShopProductRegistry.requirements(
                Objects.requireNonNull(context, "context"));
    }
}
