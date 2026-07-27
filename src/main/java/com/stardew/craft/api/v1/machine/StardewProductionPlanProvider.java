package com.stardew.craft.api.v1.machine;

import java.util.Optional;

/** Ordered pre-consumption transform; an empty result vetoes this production attempt. */
@FunctionalInterface
public interface StardewProductionPlanProvider {
    Optional<StardewProductionPlan> resolve(
            StardewProductionContext context,
            StardewProductionPlan proposed);
}
