package com.stardew.craft.api.v1.fishpond;

import com.stardew.craft.api.v1.internal.fishpond.StardewFishPondEventRegistry;
import net.minecraft.resources.ResourceLocation;

/** Registration facade for ordered, server-side fish pond notifications. */
public final class StardewFishPondEvents {
    private StardewFishPondEvents() {
    }

    public static void registerDailyListener(
            ResourceLocation id,
            int priority,
            StardewFishPondDailyListener listener
    ) {
        StardewFishPondEventRegistry.registerDailyListener(
                id, priority, listener);
    }

    public static void registerRequestListener(
            ResourceLocation id,
            int priority,
            StardewFishPondRequestListener listener
    ) {
        StardewFishPondEventRegistry.registerRequestListener(
                id, priority, listener);
    }
}
