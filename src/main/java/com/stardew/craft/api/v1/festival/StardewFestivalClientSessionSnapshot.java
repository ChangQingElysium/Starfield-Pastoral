package com.stardew.craft.api.v1.festival;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * Bounded, privacy-safe festival session view synchronized to one client.
 *
 * <p>Participant UUIDs and addon persistent data intentionally remain
 * server-side.
 */
public record StardewFestivalClientSessionSnapshot(
        ResourceLocation festivalId,
        String runtimeId,
        int year,
        int season,
        int day,
        StardewFestivalSessionSnapshot.Phase phase,
        StardewFestivalSessionSnapshot.MapPhase mapPhase,
        int participantCount,
        boolean localPlayerParticipating
) {
    public StardewFestivalClientSessionSnapshot {
        festivalId = Objects.requireNonNull(festivalId, "festivalId");
        runtimeId = Objects.requireNonNull(runtimeId, "runtimeId");
        phase = Objects.requireNonNull(phase, "phase");
        mapPhase = Objects.requireNonNull(mapPhase, "mapPhase");
        if (participantCount < 0) {
            throw new IllegalArgumentException(
                    "participantCount must be non-negative");
        }
    }
}
