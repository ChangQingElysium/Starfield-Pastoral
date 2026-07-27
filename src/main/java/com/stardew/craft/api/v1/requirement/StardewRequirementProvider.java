package com.stardew.craft.api.v1.requirement;

import com.stardew.craft.api.v1.condition.StardewCondition;
import com.stardew.craft.api.v1.condition.StardewConditionContext;

import javax.annotation.Nullable;

/** Composes display metadata without changing the authoritative condition result. */
@FunctionalInterface
public interface StardewRequirementProvider {
    /**
     * Returns a replacement display row, or {@code null} to keep the proposed row.
     * Implementations must preserve its condition type and evaluated state.
     */
    @Nullable
    StardewRequirement describe(
            StardewConditionContext context,
            StardewCondition condition,
            StardewRequirement proposed
    );
}
