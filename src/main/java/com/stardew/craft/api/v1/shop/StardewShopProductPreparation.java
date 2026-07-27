package com.stardew.craft.api.v1.shop;

import java.util.Objects;
import java.util.Optional;

/** Immutable pre-payment decision with an exact post-payment delivery callback. */
public record StardewShopProductPreparation(
        StardewShopProductDecision decision,
        Optional<StardewShopProductGrant> grant
) {
    public StardewShopProductPreparation {
        Objects.requireNonNull(decision, "decision");
        grant = Objects.requireNonNull(grant, "grant");
        if ((decision == StardewShopProductDecision.ACCEPT)
                != grant.isPresent()) {
            throw new IllegalArgumentException(
                    "only accepted preparations have a grant");
        }
    }

    public static StardewShopProductPreparation pass() {
        return new StardewShopProductPreparation(
                StardewShopProductDecision.PASS,
                Optional.empty());
    }

    public static StardewShopProductPreparation reject() {
        return new StardewShopProductPreparation(
                StardewShopProductDecision.REJECT,
                Optional.empty());
    }

    public static StardewShopProductPreparation accept(
            StardewShopProductGrant grant
    ) {
        return new StardewShopProductPreparation(
                StardewShopProductDecision.ACCEPT,
                Optional.of(Objects.requireNonNull(
                        grant, "grant")));
    }
}
