package com.stardew.craft.api.v1.internal.shop;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.action.StardewActionContext;
import com.stardew.craft.api.v1.action.StardewActions;
import com.stardew.craft.api.v1.condition.StardewConditionContext;
import com.stardew.craft.api.v1.condition.StardewConditions;
import com.stardew.craft.api.v1.requirement.StardewRequirement;
import com.stardew.craft.api.v1.requirement.StardewRequirementReport;
import com.stardew.craft.api.v1.requirement.StardewRequirementTypes;
import com.stardew.craft.api.v1.shop.StardewShopProductContext;
import com.stardew.craft.api.v1.shop.StardewShopProductDecision;
import com.stardew.craft.api.v1.shop.StardewShopProductHandler;
import com.stardew.craft.api.v1.shop.StardewShopProductPreparation;
import com.stardew.craft.api.v1.shop.StardewShopProductRule;
import com.stardew.craft.shop.ShopDataLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Comparator;
import java.util.List;

/** Built-in bridge from reloadable product actions to the ordered Java product pipeline. */
final class StardewDataShopProductHandler
        implements StardewShopProductHandler {
    @Override
    public StardewShopProductPreparation prepare(
            StardewShopProductContext context
    ) {
        var conditionContext =
                StardewConditionContext.forPlayer(
                        context.player());
        var rules = ShopDataLoader.productRuleSnapshot()
                .definitions().entrySet().stream()
                .filter(entry -> entry.getValue().shop()
                        .equals(context.shopId()))
                .filter(entry -> entry.getValue().item()
                        .equals(context.entry().item()))
                .sorted(Comparator
                        .<java.util.Map.Entry<
                                ResourceLocation,
                                StardewShopProductRule>>
                                comparingInt(entry ->
                                        entry.getValue().priority())
                        .reversed()
                        .thenComparing(entry ->
                                entry.getKey().toString()))
                .toList();
        for (var entry : rules) {
            try {
                boolean available = entry.getValue()
                        .availableWhen().stream()
                        .allMatch(condition -> StardewConditions
                                .test(condition, conditionContext)
                                .result().orElse(false));
                if (!available) {
                    continue;
                }
                if (context.quantity() != 1) {
                    return StardewShopProductPreparation.reject();
                }
                var action = entry.getValue().action();
                ResourceLocation ruleId = entry.getKey();
                return StardewShopProductPreparation.accept(
                        grantContext -> StardewActions.execute(
                                        action,
                                        StardewActionContext.forPlayer(
                                                grantContext.player()))
                                .resultOrPartial(message ->
                                        StardewCraft.LOGGER.error(
                                                "Shop product action {} "
                                                        + "failed: {}",
                                                ruleId, message))
                                .map(result -> {
                                    if (!result.success()) {
                                        StardewCraft.LOGGER.warn(
                                                "Shop product action {} "
                                                        + "rejected: {}",
                                                ruleId,
                                                result.message());
                                    }
                                    return result.success();
                                })
                                .orElse(false));
            } catch (RuntimeException exception) {
                StardewCraft.LOGGER.error(
                        "Shop product rule {} failed for {} / {}",
                        entry.getKey(), context.shopId(),
                        context.entry().item(), exception);
            }
        }
        return StardewShopProductPreparation.pass();
    }

    @Override
    public StardewRequirementReport requirements(
            StardewShopProductContext context,
            StardewShopProductDecision decision
    ) {
        if (decision != StardewShopProductDecision.REJECT) {
            return StardewShopProductHandler.super.requirements(
                    context, decision);
        }
        return new StardewRequirementReport(List.of(
                new StardewRequirement(
                        StardewRequirementTypes
                                .SHOP_PRODUCT_ACCEPTED,
                        StardewRequirement.State.UNSATISFIED,
                        Component.translatable(
                                "stardewcraft.requirement.shop.product.single",
                                context.quantity()),
                        true)));
    }
}
