package com.stardew.craft.api.v1.progress;

/** Committed lifecycle transition emitted after the owning system mutates state. */
public enum StardewProgressEventType {
    MADE_AVAILABLE,
    SCHEDULED,
    ACCEPTED,
    PROGRESSED,
    REWARD_BECAME_AVAILABLE,
    REWARD_CLAIMED,
    COMPLETED,
    FAILED,
    EXPIRED,
    CANCELLED,
    BECAME_UNAVAILABLE
}
