package com.stardew.craft.api.v1.internal.fishing;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.fishing.StardewFishingLocationDisplays;
import com.stardew.craft.api.v1.internal.extension.OrderedExtensionRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Internal fishing location label dispatch. */
public final class StardewFishingLocationDisplayRegistry {
    private static final OrderedExtensionRegistry<
            StardewFishingLocationDisplays.Provider> PROVIDERS =
            new OrderedExtensionRegistry<>(
                    ResourceLocation.fromNamespaceAndPath(
                            StardewCraft.MODID, "fishing/location_display"));

    private StardewFishingLocationDisplayRegistry() {
    }

    public static void register(
            ResourceLocation id,
            int priority,
            StardewFishingLocationDisplays.Provider provider
    ) {
        PROVIDERS.register(id, priority, provider);
    }

    public static Component resolve(String rawLocationKey) {
        if (rawLocationKey == null || rawLocationKey.isBlank()) {
            return null;
        }
        for (var registered : PROVIDERS.entries()) {
            try {
                Component result =
                        PROVIDERS.invoke(
                                registered,
                                provider -> provider.resolve(
                                        rawLocationKey));
                if (result != null) {
                    return result;
                }
            } catch (RuntimeException exception) {
                StardewCraft.LOGGER.error(
                        "Fishing location display provider {} failed for {}",
                        registered.id(), rawLocationKey, exception);
            }
        }
        return null;
    }

}
