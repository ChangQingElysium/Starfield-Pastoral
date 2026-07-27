package com.stardew.craft.api.v1.internal.world;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.internal.extension.OrderedExtensionRegistry;
import com.stardew.craft.api.v1.world.StardewLocationTransition;
import com.stardew.craft.api.v1.world.StardewLocationTransitionListener;
import net.minecraft.resources.ResourceLocation;

/** Internal dispatch bridge for logical-location observers. */
public final class StardewLocationTransitionRegistry {
    private static final OrderedExtensionRegistry<
            StardewLocationTransitionListener> LISTENERS =
            new OrderedExtensionRegistry<>(
                    ResourceLocation.fromNamespaceAndPath(
                            StardewCraft.MODID,
                            "world/location_transition"));

    private StardewLocationTransitionRegistry() {
    }

    public static void register(
            ResourceLocation id,
            int priority,
            StardewLocationTransitionListener listener
    ) {
        LISTENERS.register(id, priority, listener);
    }

    public static void dispatch(StardewLocationTransition transition) {
        for (var registered : LISTENERS.entries()) {
            try {
                LISTENERS.invokeVoid(
                        registered,
                        listener -> listener.onLocationTransition(
                                transition));
            } catch (RuntimeException exception) {
                StardewCraft.LOGGER.error(
                        "Stardew location transition listener {} failed "
                                + "during {} -> {}",
                        registered.id(),
                        transition.previousLocation(),
                        transition.currentLocation(),
                        exception);
            }
        }
    }
}
