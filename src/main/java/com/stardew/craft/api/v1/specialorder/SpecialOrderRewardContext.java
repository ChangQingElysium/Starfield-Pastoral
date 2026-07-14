package com.stardew.craft.api.v1.specialorder;

import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;

public record SpecialOrderRewardContext(ServerPlayer player, String orderId) {
    public SpecialOrderRewardContext {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(orderId, "orderId");
    }
}
