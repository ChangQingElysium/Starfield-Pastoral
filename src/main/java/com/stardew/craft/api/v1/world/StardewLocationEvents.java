package com.stardew.craft.api.v1.world;

import com.stardew.craft.api.v1.internal.world.StardewLocationTransitionRegistry;
import net.minecraft.resources.ResourceLocation;

/** Registration facade for logical-location enter/leave observers. */
public final class StardewLocationEvents {
    private StardewLocationEvents() {
    }

    public static void register(
            ResourceLocation id,
            int priority,
            StardewLocationTransitionListener listener
    ) {
        StardewLocationTransitionRegistry.register(
                id, priority, listener);
    }
}
