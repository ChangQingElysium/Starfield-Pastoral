package com.stardew.craft.api.v1.festival;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;

/** Server-authoritative context for starting one festival activity. */
public record StardewFestivalActivityContext(
        ServerPlayer player,
        ResourceLocation festivalId,
        ResourceLocation mechanicId,
        ResourceLocation activityId,
        StardewFestivalSessionSnapshot session
) {
    public StardewFestivalActivityContext {
        player = Objects.requireNonNull(player, "player");
        festivalId = Objects.requireNonNull(festivalId, "festivalId");
        mechanicId = Objects.requireNonNull(mechanicId, "mechanicId");
        activityId = Objects.requireNonNull(activityId, "activityId");
        session = Objects.requireNonNull(session, "session");
    }
}
