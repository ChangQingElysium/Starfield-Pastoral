package com.stardew.craft.api.v1.machine;

import com.stardew.craft.api.v1.internal.machine.StardewMachineCycleRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * Registration facade for plans and lifecycle observers shared by all
 * machine-cycle shapes.
 */
public final class StardewMachineCycles {
    private StardewMachineCycles() {
    }

    public static void registerPlan(
            ResourceLocation id,
            int priority,
            PlanProvider provider
    ) {
        StardewMachineCycleRegistry.registerPlan(
                id, priority, provider);
    }

    public static void registerListener(
            ResourceLocation id,
            int priority,
            Listener listener
    ) {
        StardewMachineCycleRegistry.registerListener(
                id, priority, listener);
    }

    /**
     * Resolves all registered cycle plans. Machine owners call this before
     * consuming input or committing an environmental trigger.
     */
    public static Optional<StardewProductionPlan> resolve(
            StardewMachineCycleContext context,
            StardewProductionPlan proposed
    ) {
        return StardewMachineCycleRegistry.resolve(
                context, proposed);
    }

    /**
     * Announces an already committed transition from an addon-owned machine.
     * This does not mutate the world or grant the event output.
     */
    public static void announce(StardewMachineCycleEvent event) {
        StardewMachineCycleRegistry.dispatch(event);
    }

    @FunctionalInterface
    public interface PlanProvider {
        /**
         * Returns the next plan, or empty to reject this cycle before any
         * input or environmental trigger is committed.
         */
        Optional<StardewProductionPlan> resolve(
                StardewMachineCycleContext context,
                StardewProductionPlan proposed);
    }

    @FunctionalInterface
    public interface Listener {
        void onTransition(StardewMachineCycleEvent event);
    }
}
