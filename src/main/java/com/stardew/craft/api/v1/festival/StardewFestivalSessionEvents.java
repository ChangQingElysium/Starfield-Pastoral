package com.stardew.craft.api.v1.festival;

import com.stardew.craft.api.v1.internal.festival.StardewFestivalSessionEventRegistry;
import net.minecraft.resources.ResourceLocation;

/** Ordered, failure-isolated festival session lifecycle observers. */
public final class StardewFestivalSessionEvents {
    private StardewFestivalSessionEvents() {
    }

    public static void register(
            ResourceLocation registrationId,
            int priority,
            StardewFestivalSessionListener listener
    ) {
        StardewFestivalSessionEventRegistry.register(
                registrationId, priority, listener);
    }
}
