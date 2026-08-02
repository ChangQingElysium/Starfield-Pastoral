package com.stardew.craft.combat;

/** Authoritative projection of Stardew's MeleeWeapon swing timing. */
public final class StardewWeaponSpeedRules {
    private static final double BASE_SWIPE_MILLISECONDS = 400.0D;
    private static final double MILLISECONDS_PER_RAW_SPEED = 40.0D;
    private static final double RAW_SPEED_PER_DISPLAY_POINT = 2.0D;
    private static final double CLUB_BASE_RAW_SPEED = -8.0D;
    private static final double MINIMUM_REPEAT_MILLISECONDS = 50.0D;

    private StardewWeaponSpeedRules() {
    }

    /** Converts the tooltip speed stored by this project back to game data. */
    public static int rawSpeed(WeaponType type, int displayedSpeed) {
        int raw = (int) (displayedSpeed * RAW_SPEED_PER_DISPLAY_POINT);
        return type == WeaponType.CLUB ? (int) CLUB_BASE_RAW_SPEED + raw : raw;
    }

    /** Reproduces C# integer division used by Stardew's weapon Tooltip. */
    public static int displayedSpeed(WeaponType type, int rawSpeed) {
        int baseline = type == WeaponType.CLUB ? (int) CLUB_BASE_RAW_SPEED : 0;
        return (rawSpeed - baseline) / (int) RAW_SPEED_PER_DISPLAY_POINT;
    }

    /**
     * Reproduces setFarmerAnimating/doSwipe timing and the earliest legal
     * queued repeat frame used by MeleeWeapon.leftClick.
     */
    public static double repeatMilliseconds(
            WeaponType type,
            int displayedSpeed,
            float weaponSpeedMultiplier
    ) {
        return repeatMillisecondsFromRawSpeed(
                type,
                rawSpeed(type, displayedSpeed),
                weaponSpeedMultiplier
        );
    }

    public static double repeatMillisecondsFromRawSpeed(
            WeaponType type,
            int rawSpeed,
            float weaponSpeedMultiplier
    ) {
        double swipeMilliseconds = BASE_SWIPE_MILLISECONDS
                - rawSpeed
                * MILLISECONDS_PER_RAW_SPEED;
        double animationFactor = switch (type) {
            case SWORD -> 1.3D * 6.0D / 8.0D;
            case DAGGER -> 2.0D / 4.0D;
            case CLUB -> 1.3D * 6.0D / 5.0D;
            case SLINGSHOT -> 1.0D;
        };
        double speedMultiplier = Math.max(
                0.05D,
                1.0D - Math.max(
                        0.0D,
                        Math.min(0.95D, weaponSpeedMultiplier)
                )
        );
        return Math.max(
                MINIMUM_REPEAT_MILLISECONDS,
                swipeMilliseconds * animationFactor * speedMultiplier
        );
    }

    public static double attacksPerSecond(
            WeaponType type,
            int displayedSpeed,
            float weaponSpeedMultiplier
    ) {
        return 1000.0D / repeatMilliseconds(
                type,
                displayedSpeed,
                weaponSpeedMultiplier
        );
    }

    public static double attacksPerSecondFromRawSpeed(
            WeaponType type,
            int rawSpeed,
            float weaponSpeedMultiplier
    ) {
        return 1000.0D / repeatMillisecondsFromRawSpeed(
                type,
                rawSpeed,
                weaponSpeedMultiplier
        );
    }

    public static int recoveryTicks(
            WeaponType type,
            int displayedSpeed,
            float weaponSpeedMultiplier
    ) {
        return Math.max(
                1,
                (int) Math.ceil(
                        repeatMilliseconds(
                                type,
                                displayedSpeed,
                                weaponSpeedMultiplier
                        ) / 50.0D
                )
        );
    }

    public static int recoveryTicksFromRawSpeed(
            WeaponType type,
            int rawSpeed,
            float weaponSpeedMultiplier
    ) {
        return Math.max(
                1,
                (int) Math.ceil(
                        repeatMillisecondsFromRawSpeed(
                                type,
                                rawSpeed,
                                weaponSpeedMultiplier
                        ) / 50.0D
                )
        );
    }
}
