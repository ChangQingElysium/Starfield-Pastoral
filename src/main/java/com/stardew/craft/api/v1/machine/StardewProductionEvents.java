package com.stardew.craft.api.v1.machine;

import com.stardew.craft.api.v1.internal.machine.StardewProductionEventRegistry;
import net.minecraft.resources.ResourceLocation;

/** Registration facade for ordered timed-production lifecycle observers. */
public final class StardewProductionEvents {
    private StardewProductionEvents() {
    }

    public static void register(
            ResourceLocation id,
            int priority,
            StardewProductionListener listener
    ) {
        StardewProductionEventRegistry.register(
                id, priority, listener);
    }
}
