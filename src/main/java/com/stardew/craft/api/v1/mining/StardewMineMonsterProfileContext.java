package com.stardew.craft.api.v1.mining;

import net.minecraft.server.level.ServerLevel;

import java.util.Objects;

/** Server-authoritative context used while applying a selected monster profile. */
public record StardewMineMonsterProfileContext(
        ServerLevel level,
        int floor
) {
    public StardewMineMonsterProfileContext {
        level = Objects.requireNonNull(level, "level");
        floor = Math.max(1, floor);
    }
}
