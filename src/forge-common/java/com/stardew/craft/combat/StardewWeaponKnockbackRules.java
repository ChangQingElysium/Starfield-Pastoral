package com.stardew.craft.combat;

/** Stardew raw Knockback semantics and their single Minecraft projection. */
public final class StardewWeaponKnockbackRules {
    private StardewWeaponKnockbackRules() {
    }

    public static float defaultRawKnockback(WeaponType type) {
        return switch (type) {
            case SWORD -> 1.0F;
            case DAGGER -> 0.5F;
            case CLUB -> 1.5F;
            case SLINGSHOT -> 1.0F;
        };
    }

    /** Original Tooltip weight is the signed delta from the type default, in tenths. */
    public static int tooltipWeightPoints(WeaponType type, float rawKnockback) {
        float difference = rawKnockback - defaultRawKnockback(type);
        if (Math.abs(difference) < 0.00001F) {
            return 0;
        }
        int magnitude = (int) Math.ceil(Math.abs(difference) * 10.0F);
        return difference > 0.0F ? magnitude : -magnitude;
    }

    /** Preserves Stardew's type-relative knockback strength in Minecraft units. */
    public static float minecraftStrength(WeaponType type, float rawKnockback) {
        if (!Float.isFinite(rawKnockback) || rawKnockback <= 0.0F) {
            return 0.0F;
        }
        float minecraftDefault = switch (type) {
            case SWORD -> 0.4F;
            case DAGGER -> 0.1F;
            case CLUB -> 0.8F;
            case SLINGSHOT -> 0.3F;
        };
        return Math.max(
                0.0F,
                minecraftDefault
                        + rawKnockback
                        - defaultRawKnockback(type)
        );
    }
}
