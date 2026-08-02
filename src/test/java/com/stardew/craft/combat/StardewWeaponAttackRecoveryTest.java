package com.stardew.craft.combat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StardewWeaponAttackRecoveryTest {
    @Test
    void weaponTypeAndSpeedDetermineWholeSwingFrequency() {
        assertEquals(
                8,
                StardewWeaponAttackRecovery.recoveryTicks(
                        WeaponType.SWORD,
                        0,
                        0.0F
                )
        );
        assertEquals(
                4,
                StardewWeaponAttackRecovery.recoveryTicks(
                        WeaponType.DAGGER,
                        0,
                        0.0F
                )
        );
        assertEquals(
                23,
                StardewWeaponAttackRecovery.recoveryTicks(
                        WeaponType.CLUB,
                        0,
                        0.0F
                )
        );
        assertEquals(
                2,
                StardewWeaponAttackRecovery.recoveryTicks(
                        WeaponType.SWORD,
                        4,
                        0.0F
                )
        );
        assertEquals(
                15,
                StardewWeaponAttackRecovery.recoveryTicks(
                        WeaponType.SWORD,
                        -4,
                        0.0F
                )
        );
    }

    @Test
    void equipmentWeaponSpeedReducesRecoveryDuration() {
        assertEquals(
                8,
                StardewWeaponAttackRecovery.recoveryTicks(
                        WeaponType.SWORD,
                        0,
                        0.10F
                )
        );
        assertEquals(
                7,
                StardewWeaponAttackRecovery.recoveryTicks(
                        WeaponType.SWORD,
                        0,
                        0.20F
                )
        );
    }

    @Test
    void rejectedEarlyAttackDoesNotMoveTheReadyTick() {
        UUID playerId = UUID.randomUUID();
        try {
            assertTrue(StardewWeaponAttackRecovery.tryAcquire(
                    playerId,
                    100L,
                    13
            ));
            assertFalse(StardewWeaponAttackRecovery.tryAcquire(
                    playerId,
                    112L,
                    13
            ));
            assertTrue(StardewWeaponAttackRecovery.tryAcquire(
                    playerId,
                    113L,
                    13
            ));
        } finally {
            StardewWeaponAttackRecovery.clear(playerId);
        }
    }

    @Test
    void recoveryIsPlayerGlobalRatherThanTargetOrWeaponLocal() {
        UUID playerId = UUID.randomUUID();
        try {
            assertTrue(StardewWeaponAttackRecovery.tryAcquire(
                    playerId,
                    30L,
                    8
            ));
            assertFalse(StardewWeaponAttackRecovery.tryAcquire(
                    playerId,
                    31L,
                    27
            ));
            assertTrue(StardewWeaponAttackRecovery.tryAcquire(
                    playerId,
                    38L,
                    27
            ));
        } finally {
            StardewWeaponAttackRecovery.clear(playerId);
        }
    }

    @Test
    void fractionalIntervalsCarryRemainderAcrossRepeatedSwings() {
        UUID playerId = UUID.randomUUID();
        try {
            assertTrue(StardewWeaponAttackRecovery.tryAcquire(
                    playerId, 0L, 7.02D
            ));
            assertFalse(StardewWeaponAttackRecovery.tryAcquire(
                    playerId, 7L, 7.02D
            ));
            assertTrue(StardewWeaponAttackRecovery.tryAcquire(
                    playerId, 8L, 7.02D
            ));
            assertFalse(StardewWeaponAttackRecovery.tryAcquire(
                    playerId, 14L, 7.02D
            ));
            assertTrue(StardewWeaponAttackRecovery.tryAcquire(
                    playerId, 15L, 7.02D
            ));
        } finally {
            StardewWeaponAttackRecovery.clear(playerId);
        }
    }
}
