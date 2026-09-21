package com.stardew.craft.network.overnight;

/**
 * Pure timing rules for the overnight collapse presentation.
 *
 * <p>This mirrors Stardew Valley's {@code FarmerSprite} animation 293:
 * 1000 ms drowsy, 500 ms neutral, 1000 ms drowsy, 200 ms drop, then three
 * 2000 ms prone frames. Minecraft uses 20 ticks per second, so the authored
 * sequence lasts 174 ticks. The final prone frame is also the transition to
 * black; black is then held for as long as multiplayer settlement takes.</p>
 */
public final class OvernightCollapseTimeline {
    public static final int FIRST_DROWSY_END_TICK = 20;
    public static final int NEUTRAL_END_TICK = 30;
    public static final int SECOND_DROWSY_END_TICK = 50;
    public static final int DROP_END_TICK = 54;
    public static final int SLEEP_EMOTE_TICK = 94;
    public static final int PASS_OUT_REQUEST_TICK = 134;
    public static final int COLLAPSE_TICKS = 174;
    public static final int FADE_START_TICK = PASS_OUT_REQUEST_TICK;

    private OvernightCollapseTimeline() {
    }

    public static float collapseProgress(int elapsedTicks, float partialTick) {
        return clamp((elapsedTicks + partialTick - SECOND_DROWSY_END_TICK)
            / (DROP_END_TICK - (float) SECOND_DROWSY_END_TICK));
    }

    /**
     * A small whole-body nod used during the two frame-16 drowsy beats.
     * The value is deliberately zero while the original animation shows
     * neutral frame 0 and after the player drops into frame 5.
     */
    public static float drowsyNodDegrees(int elapsedTicks, float partialTick) {
        float time = Math.max(0.0F, elapsedTicks + partialTick);
        if (time < FIRST_DROWSY_END_TICK) {
            return 12.0F * pulse(time / FIRST_DROWSY_END_TICK);
        }
        if (time >= NEUTRAL_END_TICK && time < SECOND_DROWSY_END_TICK) {
            return 12.0F * pulse((time - NEUTRAL_END_TICK)
                / (SECOND_DROWSY_END_TICK - (float) NEUTRAL_END_TICK));
        }
        return 0.0F;
    }

    public static float blackAlpha(int elapsedTicks, float partialTick) {
        return clamp((elapsedTicks + partialTick - FADE_START_TICK)
            / (COLLAPSE_TICKS - (float) FADE_START_TICK));
    }

    public static boolean canOpenSettlement(int elapsedTicks, boolean settlementReady) {
        return settlementReady && elapsedTicks >= COLLAPSE_TICKS;
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static float pulse(float progress) {
        float clamped = clamp(progress);
        return (float) Math.sin(clamped * Math.PI);
    }
}
