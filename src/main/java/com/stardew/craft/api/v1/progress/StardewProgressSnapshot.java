package com.stardew.craft.api.v1.progress;

import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

/** Immutable, read-only state projected by a progress-owning system. */
public record StardewProgressSnapshot(
        StardewProgressKey key,
        StardewProgressScope scope,
        StardewProgressPhase phase,
        List<StardewProgressMetric> metrics,
        boolean definitionAvailable,
        boolean rewardClaimable,
        OptionalInt remainingDays
) {
    public StardewProgressSnapshot {
        key = Objects.requireNonNull(key, "key");
        scope = Objects.requireNonNull(scope, "scope");
        phase = Objects.requireNonNull(phase, "phase");
        metrics = List.copyOf(metrics);
        remainingDays = Objects.requireNonNull(remainingDays, "remainingDays");
        if (remainingDays.isPresent() && remainingDays.getAsInt() < 0) {
            throw new IllegalArgumentException("remainingDays must not be negative");
        }
        if (rewardClaimable && phase != StardewProgressPhase.REWARD_AVAILABLE) {
            throw new IllegalArgumentException("rewardClaimable requires REWARD_AVAILABLE phase");
        }
    }
}
