package com.stardew.craft.animal.service;

/** Pure policy for accepting a persisted managed-animal projection. */
public final class AnimalEntityProjectionPolicy {
    private AnimalEntityProjectionPolicy() {
    }

    public enum JoinDecision {
        ACCEPT_ACTIVE,
        ACCEPT_PAUSED,
        DISCARD_NO_RECORD,
        DISCARD_NO_BUILDING,
        DISCARD_WRONG_DIMENSION
    }

    public static JoinDecision decideJoin(
            boolean recordExists,
            boolean buildingExists,
            boolean buildingGameplayEnabled,
            boolean dimensionMatches
    ) {
        if (!recordExists) {
            return JoinDecision.DISCARD_NO_RECORD;
        }
        if (!buildingExists) {
            return JoinDecision.DISCARD_NO_BUILDING;
        }
        if (!dimensionMatches) {
            return JoinDecision.DISCARD_WRONG_DIMENSION;
        }
        return buildingGameplayEnabled
                ? JoinDecision.ACCEPT_ACTIVE
                : JoinDecision.ACCEPT_PAUSED;
    }
}
