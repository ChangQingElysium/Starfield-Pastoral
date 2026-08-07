package com.stardew.craft.client.combat;

/**
 * Deterministic presentation timing for a Stardew-style combat knockout.
 *
 * <p>The original sets the farmer's collapse frame, starts a held red glow, applies jitter,
 * pauses for three seconds before the rescue transition, and stops the music. Minecraft uses a
 * short animated transition into the same prone pose during that three-second presentation.</p>
 */
public final class CombatCollapseTimeline {
    public static final int TOTAL_TICKS = 60;
    public static final int BODY_FALL_TICKS = 12;
    public static final int JITTER_TICKS = 30;
    public static final int RED_RAMP_TICKS = 8;
    public static final int RED_HOLD_TICKS = 28;
    public static final int RED_END_TICK = 45;
    public static final int BLACK_FADE_START_TICK = 40;
    public static final int BLACK_FULL_TICK = 60;
    public static final float MAX_RED_ALPHA = 0.35F;

    private CombatCollapseTimeline() {
    }

    public static float bodyFallProgress(int elapsedTicks, float partialTick) {
        return clamp((elapsedTicks + partialTick) / BODY_FALL_TICKS);
    }

    public static float jitterStrength(int elapsedTicks, float partialTick) {
        return 1.0F - clamp((elapsedTicks + partialTick) / JITTER_TICKS);
    }

    public static float redAlpha(int elapsedTicks, float partialTick) {
        float time = elapsedTicks + partialTick;
        if (time <= RED_RAMP_TICKS) {
            return MAX_RED_ALPHA * clamp(time / RED_RAMP_TICKS);
        }
        if (time <= RED_HOLD_TICKS) {
            return MAX_RED_ALPHA;
        }
        if (time >= RED_END_TICK) {
            return 0.0F;
        }
        return MAX_RED_ALPHA
            * (1.0F - clamp((time - RED_HOLD_TICKS) / (RED_END_TICK - RED_HOLD_TICKS)));
    }

    public static float blackAlpha(int elapsedTicks, float partialTick) {
        return clamp((elapsedTicks + partialTick - BLACK_FADE_START_TICK)
            / (BLACK_FULL_TICK - (float) BLACK_FADE_START_TICK));
    }

    public static boolean shouldAcknowledge(int elapsedTicks) {
        return elapsedTicks >= TOTAL_TICKS;
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
