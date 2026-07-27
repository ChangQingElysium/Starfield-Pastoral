package com.stardew.craft.api.v1.festival;

/** Ordered provider that may own one festival reward ID. */
@FunctionalInterface
public interface StardewFestivalRewardHandler {
    StardewFestivalRewardPreparation prepare(
            StardewFestivalRewardContext context);
}
