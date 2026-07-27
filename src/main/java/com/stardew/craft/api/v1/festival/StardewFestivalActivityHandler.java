package com.stardew.craft.api.v1.festival;

/** Starts or rejects one matching festival activity. */
@FunctionalInterface
public interface StardewFestivalActivityHandler {
    StardewFestivalActivityDecision start(
            StardewFestivalActivityContext context);
}
