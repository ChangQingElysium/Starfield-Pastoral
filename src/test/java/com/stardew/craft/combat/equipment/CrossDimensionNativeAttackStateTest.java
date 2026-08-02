package com.stardew.craft.combat.equipment;

import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrossDimensionNativeAttackStateTest {
    @Test
    void nativeHitFramesConsumeNestedDamageByExactTargetAndSource() {
        CrossDimensionNativeAttackHandler.NativeHitFrameStore frames =
                new CrossDimensionNativeAttackHandler.NativeHitFrameStore();
        UUID playerId = UUID.randomUUID();
        UUID outerTarget = UUID.randomUUID();
        UUID innerTarget = UUID.randomUUID();
        Object outerSource = new Object();
        Object innerSource = new Object();

        frames.bind(playerId, outerTarget, outerSource, 12L);
        frames.bind(playerId, innerTarget, innerSource, 12L);

        assertFalse(frames.consume(
                playerId,
                outerTarget,
                outerSource,
                10L
        ));
        assertFalse(frames.consume(
                playerId,
                innerTarget,
                new Object(),
                10L
        ));
        assertEquals(2, frames.size(playerId));
        assertTrue(frames.consume(
                playerId,
                innerTarget,
                innerSource,
                10L
        ));
        assertTrue(frames.consume(
                playerId,
                outerTarget,
                outerSource,
                10L
        ));
        assertEquals(0, frames.size(playerId));
    }

    @Test
    void nativeHitFramesExpireAndCleanupWithoutLeaking() {
        CrossDimensionNativeAttackHandler.NativeHitFrameStore frames =
                new CrossDimensionNativeAttackHandler.NativeHitFrameStore();
        UUID playerId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        Object source = new Object();

        frames.bind(playerId, targetId, source, 20L);
        assertFalse(frames.consume(playerId, targetId, source, 21L));
        assertEquals(0, frames.size(playerId));

        frames.bind(playerId, targetId, source, 30L);
        frames.clear(playerId);
        assertEquals(0, frames.size(playerId));
    }

    @Test
    void releaseSnapshotSkillCannotBecomeNativeAfterMainHandSwap() {
        assertTrue(CrossDimensionNativeAttackHandler.shouldBindNativeHit(
                true,
                false,
                false,
                false
        ));
        assertFalse(CrossDimensionNativeAttackHandler.shouldBindNativeHit(
                true,
                false,
                true,
                false
        ));
        assertFalse(CrossDimensionNativeAttackHandler.shouldBindNativeHit(
                true,
                false,
                false,
                true
        ));
        assertFalse(CrossDimensionNativeAttackHandler.shouldBindNativeHit(
                true,
                true,
                false,
                false
        ));
        assertFalse(CrossDimensionNativeAttackHandler.shouldBindNativeHit(
                false,
                false,
                false,
                false
        ));
    }

    @Test
    void criticalBelongsOnlyToTheExactPrimaryTargetAndTick() {
        UUID playerId = UUID.randomUUID();
        UUID primaryId = UUID.randomUUID();
        UUID sweepId = UUID.randomUUID();
        try {
            CrossDimensionNativeAttackHandler.rememberAttack(
                    playerId,
                    primaryId,
                    42L,
                    true
            );

            assertFalse(CrossDimensionNativeAttackHandler.consumeCritical(
                    playerId,
                    sweepId,
                    42L
            ));
            assertTrue(CrossDimensionNativeAttackHandler.consumeCritical(
                    playerId,
                    primaryId,
                    42L
            ));
            assertFalse(CrossDimensionNativeAttackHandler.consumeCritical(
                    playerId,
                    primaryId,
                    42L
            ));
        } finally {
            CrossDimensionNativeAttackHandler.clear(playerId);
        }
    }

    @Test
    void staleCriticalCannotLeakIntoALaterAttack() {
        UUID playerId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        try {
            CrossDimensionNativeAttackHandler.rememberAttack(
                    playerId,
                    targetId,
                    42L,
                    true
            );

            assertFalse(CrossDimensionNativeAttackHandler.consumeCritical(
                    playerId,
                    targetId,
                    43L
            ));
            assertFalse(CrossDimensionNativeAttackHandler.consumeCritical(
                    playerId,
                    targetId,
                    42L
            ));
        } finally {
            CrossDimensionNativeAttackHandler.clear(playerId);
        }
    }

    @Test
    void nativeTrinketDamageUsesTheFinalOneToOneDamage() {
        assertEquals(
                7,
                CrossDimensionNativeAttackHandler.nativeTrinketDamage(7.0F)
        );
        assertEquals(
                5,
                CrossDimensionNativeAttackHandler.nativeTrinketDamage(4.6F)
        );
        assertEquals(
                0,
                CrossDimensionNativeAttackHandler.nativeTrinketDamage(0.0F)
        );
        assertEquals(
                0,
                CrossDimensionNativeAttackHandler.nativeTrinketDamage(
                        Float.NaN
                )
        );
    }
}
