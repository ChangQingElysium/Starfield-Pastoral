package com.stardew.craft.api.v1.festival;

import net.minecraft.server.level.ServerLevel;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Immutable festival session lifecycle fact emitted after state mutation. */
public record StardewFestivalSessionEvent(
        StardewFestivalSessionEventType type,
        ServerLevel level,
        StardewFestivalSessionSnapshot session,
        Optional<StardewFestivalSessionSnapshot.Phase> previousPhase,
        Optional<StardewFestivalSessionSnapshot.MapPhase> previousMapPhase,
        Optional<UUID> participant
) {
    public StardewFestivalSessionEvent {
        type = Objects.requireNonNull(type, "type");
        level = Objects.requireNonNull(level, "level");
        session = Objects.requireNonNull(session, "session");
        previousPhase = Objects.requireNonNull(
                previousPhase, "previousPhase");
        previousMapPhase = Objects.requireNonNull(
                previousMapPhase, "previousMapPhase");
        participant = Objects.requireNonNull(participant, "participant");
        switch (type) {
            case PHASE_CHANGED -> {
                if (previousPhase.isEmpty()
                        || previousMapPhase.isPresent()
                        || participant.isPresent()) {
                    throw new IllegalArgumentException(
                            "phase event has inconsistent detail");
                }
            }
            case MAP_PHASE_CHANGED -> {
                if (previousMapPhase.isEmpty()
                        || previousPhase.isPresent()
                        || participant.isPresent()) {
                    throw new IllegalArgumentException(
                            "map phase event has inconsistent detail");
                }
            }
            case PARTICIPANT_JOINED, PARTICIPANT_LEFT -> {
                if (participant.isEmpty()
                        || previousPhase.isPresent()
                        || previousMapPhase.isPresent()) {
                    throw new IllegalArgumentException(
                            "participant event has inconsistent detail");
                }
            }
        }
    }
}
