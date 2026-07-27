package com.stardew.craft.api.v1.internal.festival;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.festival.StardewFestivalSessionEvent;
import com.stardew.craft.api.v1.festival.StardewFestivalSessionListener;
import com.stardew.craft.api.v1.internal.extension.OrderedExtensionRegistry;
import net.minecraft.resources.ResourceLocation;

/** Internal ordered festival session event dispatch. */
public final class StardewFestivalSessionEventRegistry {
    private static final OrderedExtensionRegistry<
            StardewFestivalSessionListener> LISTENERS =
            new OrderedExtensionRegistry<>(
                    ResourceLocation.fromNamespaceAndPath(
                            StardewCraft.MODID, "festival/session_lifecycle"));

    private StardewFestivalSessionEventRegistry() {
    }

    public static void register(
            ResourceLocation id,
            int priority,
            StardewFestivalSessionListener listener
    ) {
        LISTENERS.register(id, priority, listener);
    }

    public static void dispatch(StardewFestivalSessionEvent event) {
        for (var registered : LISTENERS.entries()) {
            try {
                LISTENERS.invokeVoid(
                        registered,
                        listener -> listener.onSessionChanged(event));
            } catch (RuntimeException exception) {
                StardewCraft.LOGGER.error(
                        "Festival session listener {} failed for {} {}",
                        registered.id(),
                        event.session().festivalId(),
                        event.type(),
                        exception);
            }
        }
        StardewFestivalMechanicRegistry.onSessionChanged(event);
        StardewFestivalSessionSyncService.onSessionChanged(event);
        com.stardew.craft.api.v1.internal.progress
                .StardewProgressRegistry.onFestivalSessionEvent(event);
    }
}
