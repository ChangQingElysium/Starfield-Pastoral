package com.stardew.craft.api.v1.action;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;

/** Server-authoritative state available while executing a content action. */
public record StardewActionContext(
        ServerPlayer player,
        ServerLevel level
) {
    public StardewActionContext {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(level, "level");
        if (player.level() != level) {
            throw new IllegalArgumentException("Action player must be in the supplied level");
        }
    }

    public static StardewActionContext forPlayer(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        return new StardewActionContext(player, player.serverLevel());
    }
}
