package com.stardew.craft.api.v1.shop;

import com.stardew.craft.api.v1.requirement.StardewRequirementReport;

/**
 * Optional explanation companion for lambda-based virtual product handlers.
 * The report describes, but cannot replace, the handler's preparation
 * decision.
 */
@FunctionalInterface
public interface StardewShopProductRequirementProvider {
    StardewRequirementReport requirements(
            StardewShopProductContext context,
            StardewShopProductDecision decision);
}
