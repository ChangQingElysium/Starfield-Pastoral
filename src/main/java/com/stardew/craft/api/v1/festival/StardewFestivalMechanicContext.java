package com.stardew.craft.api.v1.festival;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.Objects;

/** Server-authoritative context passed to a composable festival mechanic. */
public record StardewFestivalMechanicContext(
        ServerLevel level,
        ResourceLocation festivalId,
        ResourceLocation mechanicId,
        StardewFestivalDefinition definition,
        StardewFestivalSessionSnapshot session
) {
    public StardewFestivalMechanicContext {
        level = Objects.requireNonNull(level, "level");
        festivalId = Objects.requireNonNull(festivalId, "festivalId");
        mechanicId = Objects.requireNonNull(mechanicId, "mechanicId");
        definition = Objects.requireNonNull(definition, "definition");
        session = Objects.requireNonNull(session, "session");
    }
}
