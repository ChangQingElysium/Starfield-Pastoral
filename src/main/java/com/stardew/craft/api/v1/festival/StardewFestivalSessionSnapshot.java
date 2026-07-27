package com.stardew.craft.api.v1.festival;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Immutable public view of one persisted festival session. */
public record StardewFestivalSessionSnapshot(
        ResourceLocation festivalId,
        String runtimeId,
        int year,
        int season,
        int day,
        Phase phase,
        MapPhase mapPhase,
        Set<UUID> participants
) {
    public StardewFestivalSessionSnapshot {
        festivalId = Objects.requireNonNull(festivalId, "festivalId");
        runtimeId = Objects.requireNonNull(runtimeId, "runtimeId");
        phase = Objects.requireNonNull(phase, "phase");
        mapPhase = Objects.requireNonNull(mapPhase, "mapPhase");
        participants = Set.copyOf(participants);
    }

    public enum Phase {
        SCHEDULED,
        PREPARING_MAP,
        OPEN,
        MAIN_EVENT,
        ENDING,
        RESTORING_MAP,
        CLOSED
    }

    public enum MapPhase {
        NONE,
        APPLYING,
        APPLIED,
        RESTORING,
        RESTORED
    }
}
