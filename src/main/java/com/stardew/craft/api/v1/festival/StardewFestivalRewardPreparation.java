package com.stardew.craft.api.v1.festival;

import java.util.Objects;
import java.util.Optional;

/** Two-phase preparation result for a festival reward provider. */
public record StardewFestivalRewardPreparation(
        StardewFestivalRewardDecision decision,
        Optional<StardewFestivalRewardGrant> grant
) {
    public StardewFestivalRewardPreparation {
        decision = Objects.requireNonNull(decision, "decision");
        grant = Objects.requireNonNull(grant, "grant");
        if ((decision == StardewFestivalRewardDecision.ACCEPT)
                != grant.isPresent()) {
            throw new IllegalArgumentException(
                    "only ACCEPT may carry a reward grant");
        }
    }

    public static StardewFestivalRewardPreparation pass() {
        return new StardewFestivalRewardPreparation(
                StardewFestivalRewardDecision.PASS, Optional.empty());
    }

    public static StardewFestivalRewardPreparation reject() {
        return new StardewFestivalRewardPreparation(
                StardewFestivalRewardDecision.REJECT, Optional.empty());
    }

    public static StardewFestivalRewardPreparation accept(
            StardewFestivalRewardGrant grant
    ) {
        return new StardewFestivalRewardPreparation(
                StardewFestivalRewardDecision.ACCEPT,
                Optional.of(Objects.requireNonNull(grant, "grant")));
    }
}
