package com.stardew.craft.fishing;

import net.minecraft.util.Mth;

public final class FishingCastPower {
	private static final float TICKS_PER_SECOND = 20f;
	// Stardew: castingTimerSpeed = 0.001 per ms => 1.0 per second.
	private static final float UP_TIME_SECONDS = 1.0f;
	private static final float PERIOD_SECONDS = UP_TIME_SECONDS * 2.0f;

	private FishingCastPower() {
	}

	/**
	 * Stardew-style casting power: triangle wave 0->1->0 repeating.
	 */
	public static float getCastPower01FromUsedTicks(int usedTicks) {
		if (usedTicks <= 0) {
			return 0f;
		}
		float t = usedTicks / TICKS_PER_SECOND;
		float cycle = t % PERIOD_SECONDS;
		float power = (cycle <= UP_TIME_SECONDS) ? (cycle / UP_TIME_SECONDS) : ((PERIOD_SECONDS - cycle) / UP_TIME_SECONDS);
		return Mth.clamp(power, 0f, 1f);
	}
}
