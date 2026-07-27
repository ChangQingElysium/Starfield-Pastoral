package com.stardew.craft.api.v1.fishpond;

import net.minecraft.server.level.ServerLevel;

import java.util.Objects;

/** Read-only result of processing one populated pond for one in-game day. */
public record StardewFishPondDailyContext(
        ServerLevel level,
        int absoluteDay,
        StardewFishPondSnapshot pond
) {
    public StardewFishPondDailyContext {
        level = Objects.requireNonNull(level, "level");
        pond = Objects.requireNonNull(pond, "pond");
    }
}
