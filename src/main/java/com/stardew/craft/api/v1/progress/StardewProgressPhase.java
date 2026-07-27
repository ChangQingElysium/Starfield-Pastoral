package com.stardew.craft.api.v1.progress;

/** Cross-system lifecycle phase. Domain-specific details remain in their owning APIs. */
public enum StardewProgressPhase {
    NOT_STARTED,
    AVAILABLE,
    SCHEDULED,
    ACTIVE,
    REWARD_AVAILABLE,
    COMPLETED,
    FAILED,
    EXPIRED,
    CANCELLED,
    UNAVAILABLE;

    public boolean terminal() {
        return this == COMPLETED || this == FAILED || this == EXPIRED || this == CANCELLED;
    }
}
