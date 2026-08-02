package com.stardew.craft.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StardewWeaponSpeedRulesTest {
    @Test
    void tooltipSpeedMapsBackToMeleeWeaponRawSpeed() {
        assertEquals(0.0D, StardewWeaponSpeedRules.rawSpeed(
                WeaponType.SWORD, 0
        ));
        assertEquals(8.0D, StardewWeaponSpeedRules.rawSpeed(
                WeaponType.SWORD, 4
        ));
        assertEquals(-8.0D, StardewWeaponSpeedRules.rawSpeed(
                WeaponType.CLUB, 0
        ));
        assertEquals(-12.0D, StardewWeaponSpeedRules.rawSpeed(
                WeaponType.CLUB, -2
        ));
        assertEquals(1, StardewWeaponSpeedRules.displayedSpeed(
                WeaponType.DAGGER, 3
        ));
    }

    @Test
    void exactRawSpeedKeepsOddDaggerAndEmeraldForgeTiming() {
        assertEquals(
                140.0D,
                StardewWeaponSpeedRules.repeatMillisecondsFromRawSpeed(
                        WeaponType.DAGGER, 3, 0.0F
                ),
                0.0001D
        );
        assertEquals(
                195.0D,
                StardewWeaponSpeedRules.repeatMillisecondsFromRawSpeed(
                        WeaponType.SWORD, 5, 0.0F
                ),
                0.0001D
        );
    }

    @Test
    void repeatTimingFollowsMeleeWeaponAnimationFrames() {
        assertEquals(
                390.0D,
                StardewWeaponSpeedRules.repeatMilliseconds(
                        WeaponType.SWORD, 0, 0.0F
                ),
                0.0001D
        );
        assertEquals(
                200.0D,
                StardewWeaponSpeedRules.repeatMilliseconds(
                        WeaponType.DAGGER, 0, 0.0F
                ),
                0.0001D
        );
        assertEquals(
                1123.2D,
                StardewWeaponSpeedRules.repeatMilliseconds(
                        WeaponType.CLUB, 0, 0.0F
                ),
                0.0001D
        );
    }

    @Test
    void equipmentSpeedReducesDurationLikeFarmerWeaponSpeedBuff() {
        assertEquals(
                351.0D,
                StardewWeaponSpeedRules.repeatMilliseconds(
                        WeaponType.SWORD, 0, 0.10F
                ),
                0.0001D
        );
        assertEquals(
                1000.0D / 351.0D,
                StardewWeaponSpeedRules.attacksPerSecond(
                        WeaponType.SWORD, 0, 0.10F
                ),
                0.0001D
        );
    }
}
