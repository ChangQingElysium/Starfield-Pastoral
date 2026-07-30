package com.stardew.craft.combat.skill;

import java.util.UUID;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeowmereShotTrackerTest {
    @Test
    void runtimeTimeoutUsesAnInclusiveEndBoundary() {
        long endTick = 301L;

        assertFalse(MeowmereShotTracker.hasTimedOut(
                endTick - 1L,
                endTick
        ));
        assertTrue(MeowmereShotTracker.hasTimedOut(endTick, endTick));
    }

    @Test
    void trackedProjectileIsBoundToItsCastDimension() {
        assertTrue(MeowmereShotTracker.isSameDimension(
                Level.OVERWORLD,
                Level.OVERWORLD
        ));
        assertFalse(MeowmereShotTracker.isSameDimension(
                Level.OVERWORLD,
                Level.NETHER
        ));
    }

    @Test
    void removingAnOfflineCasterWithoutStateIsIdempotent() {
        assertDoesNotThrow(() ->
                MeowmereShotTracker.removeCaster(UUID.randomUUID())
        );
    }
}
