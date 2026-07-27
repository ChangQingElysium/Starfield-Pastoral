package com.stardew.craft.api.v1.internal.machine;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.internal.extension.OrderedExtensionRegistry;
import com.stardew.craft.api.v1.machine.StardewMachineCycleContext;
import com.stardew.craft.api.v1.machine.StardewMachineCycleEvent;
import com.stardew.craft.api.v1.machine.StardewMachineCycles;
import com.stardew.craft.api.v1.machine.StardewProductionPlan;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Optional;

/** Ordered, failure-isolated pipelines for general machine cycles. */
public final class StardewMachineCycleRegistry {
    private static final OrderedExtensionRegistry<
            StardewMachineCycles.PlanProvider> PLANS =
            new OrderedExtensionRegistry<>(
                    ResourceLocation.fromNamespaceAndPath(
                            StardewCraft.MODID,
                            "machine/cycle_plan"));
    private static final OrderedExtensionRegistry<
            StardewMachineCycles.Listener> LISTENERS =
            new OrderedExtensionRegistry<>(
                    ResourceLocation.fromNamespaceAndPath(
                            StardewCraft.MODID,
                            "machine/cycle_event"));

    private StardewMachineCycleRegistry() {
    }

    public static void registerPlan(
            ResourceLocation id,
            int priority,
            StardewMachineCycles.PlanProvider provider
    ) {
        PLANS.register(id, priority,
                Objects.requireNonNull(provider, "provider"));
    }

    public static void registerListener(
            ResourceLocation id,
            int priority,
            StardewMachineCycles.Listener listener
    ) {
        LISTENERS.register(id, priority,
                Objects.requireNonNull(listener, "listener"));
    }

    public static Optional<StardewProductionPlan> resolve(
            StardewMachineCycleContext context,
            StardewProductionPlan proposed
    ) {
        Objects.requireNonNull(context, "context");
        StardewProductionPlan current =
                Objects.requireNonNull(proposed, "proposed");
        for (var entry : PLANS.entries()) {
            try {
                StardewProductionPlan proposedPlan = current;
                Optional<StardewProductionPlan> result =
                        PLANS.invoke(
                                entry,
                                provider -> provider.resolve(
                                        context, proposedPlan));
                if (result == null) {
                    StardewCraft.LOGGER.error(
                            "Machine cycle plan provider {} returned null "
                                    + "for {} {} at {}",
                            entry.id(), context.kind(),
                            context.machineId(), context.position());
                    continue;
                }
                if (result.isEmpty()) {
                    return Optional.empty();
                }
                current = result.get();
            } catch (RuntimeException exception) {
                StardewCraft.LOGGER.error(
                        "Machine cycle plan provider {} failed for "
                                + "{} {} at {}",
                        entry.id(), context.kind(),
                        context.machineId(), context.position(),
                        exception);
            }
        }
        return Optional.of(current);
    }

    public static void dispatch(StardewMachineCycleEvent event) {
        Objects.requireNonNull(event, "event");
        for (var entry : LISTENERS.entries()) {
            try {
                LISTENERS.invokeVoid(
                        entry,
                        listener -> listener.onTransition(event));
            } catch (RuntimeException exception) {
                StardewCraft.LOGGER.error(
                        "Machine cycle listener {} failed for {} {} "
                                + "{} at {}",
                        entry.id(), event.kind(), event.machineId(),
                        event.phase(), event.position(), exception);
            }
        }
    }
}
