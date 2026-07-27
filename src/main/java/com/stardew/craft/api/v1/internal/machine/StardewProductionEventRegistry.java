package com.stardew.craft.api.v1.internal.machine;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.internal.extension.OrderedExtensionRegistry;
import com.stardew.craft.api.v1.machine.StardewProductionEvent;
import com.stardew.craft.api.v1.machine.StardewProductionListener;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Ordered, failure-isolated timed-production lifecycle dispatch. */
public final class StardewProductionEventRegistry {
    private static final OrderedExtensionRegistry<
            StardewProductionListener> REGISTRY =
            new OrderedExtensionRegistry<>(
                    ResourceLocation.fromNamespaceAndPath(
                            StardewCraft.MODID,
                            "machine/production_event"));

    private StardewProductionEventRegistry() {
    }

    public static void register(
            ResourceLocation id,
            int priority,
            StardewProductionListener listener
    ) {
        REGISTRY.register(
                id, priority,
                Objects.requireNonNull(listener, "listener"));
    }

    public static void dispatch(StardewProductionEvent event) {
        Objects.requireNonNull(event, "event");
        for (var entry : REGISTRY.entries()) {
            try {
                REGISTRY.invokeVoid(
                        entry,
                        listener -> listener.onTransition(event));
            } catch (RuntimeException exception) {
                StardewCraft.LOGGER.error(
                        "Production event listener {} failed for "
                                + "{} {} at {}",
                        entry.id(), event.machineId(),
                        event.phase(), event.position(),
                        exception);
            }
        }
    }
}
