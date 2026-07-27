package com.stardew.craft.api.v1.internal.machine;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.internal.extension.OrderedExtensionRegistry;
import com.stardew.craft.api.v1.machine.StardewProductionContext;
import com.stardew.craft.api.v1.machine.StardewProductionPlan;
import com.stardew.craft.api.v1.machine.StardewProductionPlanProvider;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Optional;

/** Ordered, failure-isolated timed-production plan pipeline. */
public final class StardewProductionPlanRegistry {
    private static final OrderedExtensionRegistry<
            StardewProductionPlanProvider> REGISTRY =
            new OrderedExtensionRegistry<>(
                    ResourceLocation.fromNamespaceAndPath(
                            StardewCraft.MODID, "machine/production_plan"));

    private StardewProductionPlanRegistry() {
    }

    public static void register(
            ResourceLocation id,
            int priority,
            StardewProductionPlanProvider provider
    ) {
        REGISTRY.register(
                id, priority,
                Objects.requireNonNull(provider, "provider"));
    }

    public static Optional<StardewProductionPlan> resolve(
            StardewProductionContext context,
            StardewProductionPlan proposed
    ) {
        Objects.requireNonNull(context, "context");
        StardewProductionPlan current =
                Objects.requireNonNull(proposed, "proposed");
        for (var entry : REGISTRY.entries()) {
            try {
                StardewProductionPlan proposedPlan = current;
                Optional<StardewProductionPlan> result =
                        REGISTRY.invoke(
                                entry,
                                provider -> provider.resolve(
                                        context, proposedPlan));
                if (result == null) {
                    StardewCraft.LOGGER.error(
                            "Production plan provider {} returned null "
                                    + "for {} at {}",
                            entry.id(), context.machineId(),
                            context.position());
                    continue;
                }
                if (result.isEmpty()) {
                    return Optional.empty();
                }
                current = result.get();
            } catch (RuntimeException exception) {
                StardewCraft.LOGGER.error(
                        "Production plan provider {} failed for {} "
                                + "at {}",
                        entry.id(), context.machineId(),
                        context.position(), exception);
            }
        }
        return Optional.of(current);
    }
}
