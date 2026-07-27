package com.stardew.craft.api.v1.festival;

import java.util.Objects;
import java.util.Optional;

/** Result of a server-authoritative festival participant operation. */
public record StardewFestivalParticipantResult(
        Status status,
        Optional<StardewFestivalSessionSnapshot> session
) {
    public StardewFestivalParticipantResult {
        status = Objects.requireNonNull(status, "status");
        session = Objects.requireNonNull(session, "session");
    }

    public boolean changed() {
        return status == Status.JOINED || status == Status.LEFT;
    }

    public enum Status {
        JOINED,
        LEFT,
        ALREADY_PARTICIPATING,
        NOT_PARTICIPATING,
        FESTIVAL_NOT_FOUND,
        SESSION_NOT_OPEN
    }
}
