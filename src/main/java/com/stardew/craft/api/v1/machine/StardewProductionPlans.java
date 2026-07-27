package com.stardew.craft.api.v1.machine;

import com.stardew.craft.api.v1.internal.machine.StardewProductionPlanRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/** Registration and resolution facade for timed-machine output and duration plans. */
public final class StardewProductionPlans {
    private StardewProductionPlans() {
    }

    public static void register(
            ResourceLocation id,
            int priority,
            StardewProductionPlanProvider provider
    ) {
        StardewProductionPlanRegistry.register(
                id, priority, provider);
    }

    public static Optional<StardewProductionPlan> resolve(
            StardewProductionContext context,
            StardewProductionPlan proposed
    ) {
        return StardewProductionPlanRegistry.resolve(
                context, proposed);
    }
}
