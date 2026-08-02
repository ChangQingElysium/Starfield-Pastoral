package com.stardew.craft.client.weapon;

import com.stardew.craft.combat.network.WickedKrisPoisonStatusPayload;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WickedKrisPoisonClientStateTest {
    private static final UUID TARGET_A = UUID.fromString(
            "00000000-0000-0000-0000-00000000000a"
    );
    private static final UUID TARGET_B = UUID.fromString(
            "00000000-0000-0000-0000-00000000000b"
    );

    @BeforeEach
    @AfterEach
    void clearState() {
        WickedKrisPoisonClientState.clearAll();
    }

    @Test
    void targetScopedRemovalDoesNotClearAnotherPoison() {
        WickedKrisPoisonClientState.upsert(
                TARGET_A,
                100L,
                3,
                80,
                100,
                0,
                0
        );
        WickedKrisPoisonClientState.upsert(
                TARGET_B,
                100L,
                5,
                40,
                100,
                0,
                0
        );

        assertEquals(2, WickedKrisPoisonClientState.trackedTargetCount());
        assertEquals(5, WickedKrisPoisonClientState.getStacksAt(100L));

        WickedKrisPoisonClientState.remove(TARGET_B);

        assertEquals(1, WickedKrisPoisonClientState.trackedTargetCount());
        assertEquals(3, WickedKrisPoisonClientState.getStacksAt(100L));
        assertEquals(
                80,
                WickedKrisPoisonClientState.getRemainingTicksAt(100L)
        );
    }

    @Test
    void strongestPoisonBreaksStackTiesByLaterExpiry() {
        WickedKrisPoisonClientState.upsert(
                TARGET_A,
                200L,
                5,
                20,
                100,
                0,
                0
        );
        WickedKrisPoisonClientState.upsert(
                TARGET_B,
                200L,
                5,
                80,
                120,
                0,
                0
        );

        assertEquals(5, WickedKrisPoisonClientState.getStacksAt(200L));
        assertEquals(
                80,
                WickedKrisPoisonClientState.getRemainingTicksAt(200L)
        );
        assertEquals(
                120,
                WickedKrisPoisonClientState.getTotalTicksAt(200L)
        );
    }

    @Test
    void earliestTargetDetonationOwnsTheCountdown() {
        WickedKrisPoisonClientState.upsert(
                TARGET_A,
                300L,
                5,
                100,
                100,
                60,
                60
        );
        WickedKrisPoisonClientState.upsert(
                TARGET_B,
                300L,
                5,
                100,
                100,
                20,
                40
        );

        assertTrue(WickedKrisPoisonClientState.hasDetonationAt(300L));
        assertEquals(
                20,
                WickedKrisPoisonClientState
                        .getDetonationRemainingTicksAt(300L)
        );
        assertEquals(
                40,
                WickedKrisPoisonClientState
                        .getDetonationTotalTicksAt(300L)
        );
    }

    @Test
    void poisonOnlyRefreshPreservesThatTargetsExistingFuse() {
        WickedKrisPoisonClientState.upsert(
                TARGET_A,
                400L,
                5,
                200,
                200,
                60,
                60
        );
        WickedKrisPoisonClientState.upsert(
                TARGET_A,
                410L,
                5,
                100,
                100,
                -1,
                0
        );

        assertEquals(1, WickedKrisPoisonClientState.trackedTargetCount());
        assertEquals(
                50,
                WickedKrisPoisonClientState
                        .getDetonationRemainingTicksAt(410L)
        );
        assertEquals(
                60,
                WickedKrisPoisonClientState
                        .getDetonationTotalTicksAt(410L)
        );
        assertEquals(
                100,
                WickedKrisPoisonClientState.getRemainingTicksAt(410L)
        );
    }

    @Test
    void expiryPrunesOnlyTheExpiredProjection() {
        WickedKrisPoisonClientState.upsert(
                TARGET_A,
                500L,
                5,
                20,
                20,
                0,
                0
        );
        WickedKrisPoisonClientState.upsert(
                TARGET_B,
                500L,
                4,
                40,
                40,
                0,
                0
        );

        assertTrue(WickedKrisPoisonClientState.hasPoisonAt(520L));
        assertEquals(5, WickedKrisPoisonClientState.getStacksAt(520L));
        assertTrue(WickedKrisPoisonClientState.hasPoisonAt(521L));
        assertEquals(4, WickedKrisPoisonClientState.getStacksAt(521L));
        assertFalse(WickedKrisPoisonClientState.hasPoisonAt(541L));
        assertEquals(0, WickedKrisPoisonClientState.trackedTargetCount());
    }

    @Test
    void clearAllResetsEveryTargetProjection() {
        WickedKrisPoisonClientState.upsert(
                TARGET_A,
                0L,
                5,
                100,
                100,
                60,
                60
        );
        WickedKrisPoisonClientState.upsert(
                TARGET_B,
                0L,
                5,
                100,
                100,
                0,
                0
        );

        WickedKrisPoisonClientState.clearAll();

        assertEquals(0, WickedKrisPoisonClientState.trackedTargetCount());
        assertFalse(WickedKrisPoisonClientState.hasPoisonAt(0L));
        assertFalse(WickedKrisPoisonClientState.hasDetonationAt(0L));
    }

    @Test
    void statusNotificationsCarryTheirTargetAndExplicitOperation() {
        WickedKrisPoisonStatusPayload upsert =
                WickedKrisPoisonStatusPayload.upsert(
                        TARGET_A,
                        5,
                        100,
                        100,
                        60,
                        60
                );
        assertEquals(TARGET_A, upsert.targetId());
        assertEquals(
                WickedKrisPoisonStatusPayload.Operation.UPSERT,
                upsert.operation()
        );
        assertEquals(5, upsert.stacks());
        assertEquals(100, upsert.poisonRemainingTicks());
        assertEquals(60, upsert.detonateRemainingTicks());

        WickedKrisPoisonStatusPayload remove =
                WickedKrisPoisonStatusPayload.remove(TARGET_B);
        assertEquals(TARGET_B, remove.targetId());
        assertEquals(
                WickedKrisPoisonStatusPayload.Operation.REMOVE,
                remove.operation()
        );

        assertEquals(
                WickedKrisPoisonStatusPayload.Operation.CLEAR_ALL,
                WickedKrisPoisonStatusPayload.clearAll().operation()
        );
    }
}
