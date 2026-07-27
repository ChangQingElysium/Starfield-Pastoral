package com.stardew.craft.api.v1.shop;

import com.stardew.craft.api.v1.requirement.StardewRequirement;
import com.stardew.craft.api.v1.requirement.StardewRequirementReport;
import com.stardew.craft.api.v1.requirement.StardewRequirementTypes;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Server-authoritative delivery handler for a shop product that is not a physical item.
 *
 * <p>{@link #prepare} runs before payment and may capture the exact delivery selected from the
 * current player state. When accepted, the core pays the resolved shop cost and invokes that
 * prepared callback. Returning {@code false} or throwing from it refunds the payment.
 */
@FunctionalInterface
public interface StardewShopProductHandler {
    StardewShopProductPreparation prepare(
            StardewShopProductContext context);

    /**
     * Describes the already-authoritative preparation decision for menus and
     * diagnostics. Implementations may add detailed rows but must not report a
     * satisfied result for {@link StardewShopProductDecision#REJECT}, or an
     * unsatisfied result for {@link StardewShopProductDecision#ACCEPT}.
     */
    default StardewRequirementReport requirements(
            StardewShopProductContext context,
            StardewShopProductDecision decision
    ) {
        if (decision == StardewShopProductDecision.PASS) {
            return new StardewRequirementReport(List.of());
        }
        boolean accepted =
                decision == StardewShopProductDecision.ACCEPT;
        return new StardewRequirementReport(List.of(
                new StardewRequirement(
                        StardewRequirementTypes
                                .SHOP_PRODUCT_ACCEPTED,
                        accepted
                                ? StardewRequirement.State.SATISFIED
                                : StardewRequirement.State.UNSATISFIED,
                        Component.translatable(
                                "stardewcraft.requirement.shop.product",
                                context.entry().item()),
                        false)));
    }
}
