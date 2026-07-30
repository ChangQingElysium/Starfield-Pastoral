package com.stardew.craft.combat.skill;

import java.util.UUID;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DwarfRuneGuardTrackerTest {
    @Test
    void shelterWindowExpiresAtItsExclusiveEndBoundary() {
        long endTick = 150L;

        assertEquals(
                DwarfRuneGuardTracker.Status.ACTIVE,
                DwarfRuneGuardTracker.statusForSnapshot(
                        true,
                        endTick - 1L,
                        endTick
                )
        );
        assertEquals(
                DwarfRuneGuardTracker.Status.EXPIRED,
                DwarfRuneGuardTracker.statusForSnapshot(
                        true,
                        endTick,
                        endTick
                )
        );
    }

    @Test
    void dimensionChangeInvalidatesTheRuntimeState() {
        assertEquals(
                DwarfRuneGuardTracker.Status.INVALIDATED,
                DwarfRuneGuardTracker.statusForSnapshot(
                        false,
                        120L,
                        150L
                )
        );
        assertTrue(DwarfRuneGuardTracker.isSameDimension(
                Level.OVERWORLD,
                Level.OVERWORLD
        ));
        assertFalse(DwarfRuneGuardTracker.isSameDimension(
                Level.OVERWORLD,
                Level.NETHER
        ));
    }

    @Test
    void removingAnOfflinePlayerWithoutStateIsIdempotent() {
        assertDoesNotThrow(() ->
                DwarfRuneGuardTracker.removePlayer(UUID.randomUUID())
        );
    }
}
