package com.stardew.craft.api.v1.specialorder;

import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;

public record SpecialOrderObjectiveContext(
        ServerPlayer player,
        String orderId,
        int progress,
        int required
) {
    public SpecialOrderObjectiveContext {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(orderId, "orderId");
    }
}
