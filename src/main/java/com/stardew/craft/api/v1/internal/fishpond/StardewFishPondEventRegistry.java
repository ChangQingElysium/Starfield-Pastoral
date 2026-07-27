package com.stardew.craft.api.v1.internal.fishpond;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.fishpond.StardewFishPondDailyContext;
import com.stardew.craft.api.v1.fishpond.StardewFishPondDailyListener;
import com.stardew.craft.api.v1.fishpond.StardewFishPondRequestContext;
import com.stardew.craft.api.v1.fishpond.StardewFishPondRequestListener;
import com.stardew.craft.api.v1.internal.extension.OrderedExtensionRegistry;
import net.minecraft.resources.ResourceLocation;

/** Internal ordered dispatch for public fish pond notifications. */
public final class StardewFishPondEventRegistry {
    private static final OrderedExtensionRegistry<
            StardewFishPondDailyListener> DAILY_LISTENERS =
            new OrderedExtensionRegistry<>(
                    id("fish_pond/daily_listeners"));
    private static final OrderedExtensionRegistry<
            StardewFishPondRequestListener> REQUEST_LISTENERS =
            new OrderedExtensionRegistry<>(
                    id("fish_pond/request_listeners"));

    private StardewFishPondEventRegistry() {
    }

    public static void registerDailyListener(
            ResourceLocation id,
            int priority,
            StardewFishPondDailyListener listener
    ) {
        DAILY_LISTENERS.register(id, priority, listener);
    }

    public static void registerRequestListener(
            ResourceLocation id,
            int priority,
            StardewFishPondRequestListener listener
    ) {
        REQUEST_LISTENERS.register(id, priority, listener);
    }

    public static void announceDaily(
            StardewFishPondDailyContext context
    ) {
        for (var registration : DAILY_LISTENERS.entries()) {
            try {
                DAILY_LISTENERS.invokeVoid(
                        registration,
                        listener -> listener.onDailyUpdate(context));
            } catch (RuntimeException exception) {
                StardewCraft.LOGGER.error(
                        "Fish pond daily listener {} failed for {}",
                        registration.id(),
                        context.pond().id(),
                        exception);
            }
        }
    }

    public static void announceRequest(
            StardewFishPondRequestContext context
    ) {
        for (var registration : REQUEST_LISTENERS.entries()) {
            try {
                REQUEST_LISTENERS.invokeVoid(
                        registration,
                        listener -> listener.onRequestCompleted(context));
            } catch (RuntimeException exception) {
                StardewCraft.LOGGER.error(
                        "Fish pond request listener {} failed for {}",
                        registration.id(),
                        context.pond().id(),
                        exception);
            }
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                StardewCraft.MODID, path);
    }
}
