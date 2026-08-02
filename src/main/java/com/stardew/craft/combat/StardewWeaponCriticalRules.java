package com.stardew.craft.combat;

/** Shared Stardew weapon-only critical chance and power semantics. */
public final class StardewWeaponCriticalRules {
    private static final float DEFAULT_CRITICAL_MULTIPLIER = 3.0F;

    private StardewWeaponCriticalRules() {
    }

    public static float intrinsicChance(WeaponStats stats) {
        float chance = stats.getCritChance() + stats.getBonusCritChance();
        if (stats.getWeaponType() == WeaponType.DAGGER) {
            chance = (chance + 0.005F) * 1.12F;
        }
        return chance;
    }

    public static float displayedChance(WeaponStats stats) {
        return Math.max(0.0F, Math.min(1.0F, intrinsicChance(stats)));
    }

    public static float multiplier(WeaponStats stats) {
        float baseMultiplier = DEFAULT_CRITICAL_MULTIPLIER
                + stats.getBonusCritPower() / 50.0F;
        return baseMultiplier * Math.max(
                0.0F,
                1.0F + stats.getCritPowerMultiplierBonus()
        );
    }
}
