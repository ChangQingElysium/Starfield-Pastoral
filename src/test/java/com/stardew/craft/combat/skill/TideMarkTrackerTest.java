package com.stardew.craft.combat.skill;

import java.util.UUID;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TideMarkTrackerTest {
    @Test
    void markExpiresAtTheExclusiveEndTick() {
        long endTick = 200L;

        assertFalse(TideMarkTracker.isExpired(endTick - 1L, endTick));
        assertTrue(TideMarkTracker.isExpired(endTick, endTick));
    }

    @Test
    void remainingDurationIsBoundedAndNeverNegative() {
        assertEquals(100, TideMarkTracker.remainingDurationTicks(100L, 200L));
        assertEquals(0, TideMarkTracker.remainingDurationTicks(200L, 200L));
        assertEquals(
                Integer.MAX_VALUE,
                TideMarkTracker.remainingDurationTicks(
                        0L,
                        (long) Integer.MAX_VALUE + 1L
                )
        );
    }

    @Test
    void aDimensionTransferRequiresClientMarkResynchronization() {
        assertFalse(TideMarkTracker.shouldResyncDimension(
                Level.OVERWORLD.location().toString(),
                Level.OVERWORLD
        ));
        assertTrue(TideMarkTracker.shouldResyncDimension(
                Level.OVERWORLD.location().toString(),
                Level.NETHER
        ));
        assertTrue(TideMarkTracker.shouldResyncDimension(
                "",
                Level.OVERWORLD
        ));
    }

    @Test
    void bonusOwnershipIsBoundToTheMarkingPlayer() {
        UUID owner = UUID.fromString(
                "00000000-0000-0000-0000-000000000001"
        );
        UUID otherPlayer = UUID.fromString(
                "00000000-0000-0000-0000-000000000002"
        );

        assertTrue(TideMarkTracker.matchesOwner(owner, owner));
        assertFalse(TideMarkTracker.matchesOwner(owner, otherPlayer));
    }
}
