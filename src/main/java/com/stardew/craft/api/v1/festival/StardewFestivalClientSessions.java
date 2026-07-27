package com.stardew.craft.api.v1.festival;

import com.stardew.craft.api.v1.internal.festival.StardewFestivalClientSessionCache;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/** Read-only client cache for the latest bounded festival session snapshot. */
public final class StardewFestivalClientSessions {
    private StardewFestivalClientSessions() {
    }

    public static long revision() {
        return StardewFestivalClientSessionCache.revision();
    }

    public static List<StardewFestivalClientSessionSnapshot> all() {
        return StardewFestivalClientSessionCache.all();
    }

    public static Optional<StardewFestivalClientSessionSnapshot> find(
            ResourceLocation festivalId
    ) {
        return StardewFestivalClientSessionCache.find(festivalId);
    }
}
