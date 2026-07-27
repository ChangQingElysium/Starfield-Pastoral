package com.stardew.craft.api.v1.progress;

import com.stardew.craft.api.v1.internal.progress.StardewProgressRegistry;
import net.minecraft.resources.ResourceLocation;

/** Ordered, failure-isolated committed progress event bus. */
public final class StardewProgressEvents {
    private StardewProgressEvents() {
    }

    public static void register(
            ResourceLocation registrationId,
            int priority,
            StardewProgressListener listener
    ) {
        StardewProgressRegistry.registerListener(
                registrationId, priority, listener);
    }

    /** Announces a transition for an add-on-owned progress domain. */
    public static void announce(StardewProgressEvent event) {
        StardewProgressRegistry.dispatch(event);
    }
}
