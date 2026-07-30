package com.stardew.craft.api.v1.interaction;

@FunctionalInterface
public interface StardewInteractionHintProvider {
    StardewInteractionHintDecision resolve(
            StardewInteractionHintContext context
    );
}
