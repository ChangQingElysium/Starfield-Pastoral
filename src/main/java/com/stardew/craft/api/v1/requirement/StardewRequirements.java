package com.stardew.craft.api.v1.requirement;

import com.stardew.craft.api.v1.condition.StardewCondition;
import com.stardew.craft.api.v1.condition.StardewConditionContext;
import com.stardew.craft.api.v1.internal.requirement.StardewRequirementRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;

/** Server-authoritative condition reports for UI, diagnostics and addon composition. */
public final class StardewRequirements {
    private StardewRequirements() {
    }

    public static void register(
            ResourceLocation registrationId,
            int priority,
            StardewRequirementProvider provider
    ) {
        StardewRequirementRegistry.register(
                registrationId, priority, provider);
    }

    public static StardewRequirement evaluate(
            StardewConditionContext context,
            StardewCondition condition
    ) {
        return StardewRequirementRegistry.evaluate(
                Objects.requireNonNull(context, "context"),
                Objects.requireNonNull(condition, "condition"));
    }

    /**
     * Evaluates every condition without short-circuiting so callers can show all blockers.
     * The source list keeps its declared order and uses AND semantics.
     */
    public static StardewRequirementReport evaluateAll(
            StardewConditionContext context,
            List<StardewCondition> conditions
    ) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(conditions, "conditions");
        return new StardewRequirementReport(conditions.stream()
                .map(condition -> evaluate(context, condition))
                .toList());
    }
}
