package com.stardew.craft.api.v1.fishing;

import com.stardew.craft.api.v1.internal.fishing.StardewFishingLocationDisplayRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Client-safe labels for fishing location keys and biome tags. */
public final class StardewFishingLocationDisplays {
    private StardewFishingLocationDisplays() {
    }

    public static void register(ResourceLocation id, int priority, Provider provider) {
        StardewFishingLocationDisplayRegistry.register(id, priority, provider);
    }

    @FunctionalInterface
    public interface Provider {
        /** Returns a label for the raw location key, or {@code null} to pass. */
        Component resolve(String rawLocationKey);
    }
}
