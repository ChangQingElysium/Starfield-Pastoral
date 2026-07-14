package com.stardew.craft.api.v1.condition;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.Objects;

/** Server-authoritative state available while evaluating a condition. */
public record StardewConditionContext(
        ServerLevel level,
        @Nullable ServerPlayer player
) {
    public StardewConditionContext {
        Objects.requireNonNull(level, "level");
    }

    public static StardewConditionContext forPlayer(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        return new StardewConditionContext(player.serverLevel(), player);
    }
}
