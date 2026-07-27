package com.stardew.craft.api.v1.fishpond;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.Objects;

/** Server-authoritative notification after a fish pond request completes. */
public record StardewFishPondRequestContext(
        ServerLevel level,
        @Nullable ServerPlayer player,
        String requestedItem,
        int requestedCount,
        StardewFishPondSnapshot pond
) {
    public StardewFishPondRequestContext {
        level = Objects.requireNonNull(level, "level");
        requestedItem = Objects.requireNonNull(
                requestedItem, "requestedItem");
        pond = Objects.requireNonNull(pond, "pond");
        if (requestedItem.isBlank()) {
            throw new IllegalArgumentException(
                    "requestedItem must not be blank");
        }
        if (requestedCount <= 0) {
            throw new IllegalArgumentException(
                    "requestedCount must be positive");
        }
    }
}
