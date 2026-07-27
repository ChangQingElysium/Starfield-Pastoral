package com.stardew.craft.api.v1.festival;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Optional;

/** Machine-readable outcome of one festival reward claim. */
public record StardewFestivalRewardClaimResult(
        Status status,
        Optional<ResourceLocation> handlerId
) {
    public StardewFestivalRewardClaimResult {
        status = Objects.requireNonNull(status, "status");
        handlerId = Objects.requireNonNull(handlerId, "handlerId");
    }

    public boolean claimed() {
        return status == Status.CLAIMED;
    }

    public enum Status {
        CLAIMED,
        ALREADY_CLAIMED,
        FESTIVAL_NOT_FOUND,
        SESSION_NOT_OPEN,
        NOT_PARTICIPATING,
        REWARD_NOT_FOUND,
        REJECTED,
        GRANT_FAILED
    }
}
