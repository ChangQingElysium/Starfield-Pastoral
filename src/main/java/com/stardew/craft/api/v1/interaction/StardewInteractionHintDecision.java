package com.stardew.craft.api.v1.interaction;

import javax.annotation.Nullable;

/**
 * Tri-state provider result: pass to later providers, deliberately hide the
 * hint, or show a resolved hint.
 */
public record StardewInteractionHintDecision(
        boolean handled,
        @Nullable StardewInteractionHint hint
) {
    private static final StardewInteractionHintDecision PASS =
            new StardewInteractionHintDecision(false, null);
    private static final StardewInteractionHintDecision HIDE =
            new StardewInteractionHintDecision(true, null);

    public static StardewInteractionHintDecision pass() {
        return PASS;
    }

    public static StardewInteractionHintDecision hide() {
        return HIDE;
    }

    public static StardewInteractionHintDecision show(
            StardewInteractionHint hint
    ) {
        return new StardewInteractionHintDecision(true, hint);
    }
}
