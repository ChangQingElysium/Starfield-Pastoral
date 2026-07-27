package com.stardew.craft.api.v1.festival;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;

/** Server-authoritative context for preparing and granting one reward claim. */
public record StardewFestivalRewardContext(
        ServerPlayer player,
        ResourceLocation festivalId,
        ResourceLocation mechanicId,
        ResourceLocation rewardId,
        StardewFestivalSessionSnapshot session
) {
    public StardewFestivalRewardContext {
        player = Objects.requireNonNull(player, "player");
        festivalId = Objects.requireNonNull(festivalId, "festivalId");
        mechanicId = Objects.requireNonNull(mechanicId, "mechanicId");
        rewardId = Objects.requireNonNull(rewardId, "rewardId");
        session = Objects.requireNonNull(session, "session");
    }
}
