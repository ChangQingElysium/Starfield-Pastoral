package com.stardew.craft.api.v1.festival;

/** Commit step for a prepared festival reward. */
@FunctionalInterface
public interface StardewFestivalRewardGrant {
    boolean grant(StardewFestivalRewardContext context);
}
