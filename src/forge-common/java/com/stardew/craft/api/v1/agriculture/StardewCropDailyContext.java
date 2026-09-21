package com.stardew.craft.api.v1.agriculture;

/**
 * Authoritative world inputs for one crop's daily update.
 *
 * <p>Season uses StardewCraft's stable zero-based order: spring, summer, fall, winter.
 */
public record StardewCropDailyContext(
        boolean watered,
        int season,
        boolean seasonsIgnored,
        int absoluteDay,
        boolean offlineCatchUp
) {
    public StardewCropDailyContext {
        if (season < 0 || season > 3) {
            throw new IllegalArgumentException("Crop season must be in range 0..3");
        }
        if (absoluteDay < 0) {
            throw new IllegalArgumentException("Absolute day cannot be negative");
        }
    }
}
