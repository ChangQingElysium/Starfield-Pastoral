package com.stardew.craft.api.v1.festival;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Optional;

/** Machine-readable result of starting a festival activity. */
public record StardewFestivalActivityResult(
        Status status,
        Optional<ResourceLocation> handlerId
) {
    public StardewFestivalActivityResult {
        status = Objects.requireNonNull(status, "status");
        handlerId = Objects.requireNonNull(handlerId, "handlerId");
    }

    public boolean started() {
        return status == Status.STARTED;
    }

    public enum Status {
        STARTED,
        REJECTED,
        FESTIVAL_NOT_FOUND,
        SESSION_NOT_OPEN,
        NOT_PARTICIPATING,
        WRONG_LOCATION,
        ACTIVITY_NOT_FOUND
    }
}
