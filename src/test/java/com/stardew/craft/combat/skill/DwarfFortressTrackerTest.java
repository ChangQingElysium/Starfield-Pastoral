package com.stardew.craft.combat.skill;

import java.util.UUID;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DwarfFortressTrackerTest {
    @Test
    void shockDamageKeepsTheMajorSkillContextContract() {
        SkillContext initial =
                DwarfFortressTracker.createShockContext(2.2F);
        SkillContext reactive =
                DwarfFortressTracker.createShockContext(1.0F);
        SkillContext echo =
                DwarfFortressTracker.createShockContext(1.2F);

        assertEquals("dwarf_fortress", initial.getSkillId());
        assertEquals(SkillContext.SkillTier.MAJOR, initial.getTier());
        assertEquals(2.2F, initial.getDamageMultiplier());
        assertEquals(1.0F, reactive.getDamageMultiplier());
        assertEquals(1.2F, echo.getDamageMultiplier());
        assertFalse(initial.isIgnoreDefense());
        assertFalse(initial.isGuaranteedCrit());
        assertEquals(
                5,
                DwarfFortressTracker.HIT_CONTEXT_LIFETIME_TICKS
        );
    }

    @Test
    void activeWindowPreservesItsInclusiveEndBoundary() {
        long endTick = 180L;

        assertTrue(DwarfFortressTracker.isWithinActiveWindow(
                endTick,
                endTick
        ));
        assertFalse(DwarfFortressTracker.isWithinActiveWindow(
                endTick + 1L,
                endTick
        ));
    }

    @Test
    void reactiveShockIsLimitedByCountAndTick() {
        assertTrue(DwarfFortressTracker.canTriggerReactiveShock(
                3,
                110L,
                111L
        ));
        assertFalse(DwarfFortressTracker.canTriggerReactiveShock(
                4,
                110L,
                111L
        ));
        assertFalse(DwarfFortressTracker.canTriggerReactiveShock(
                3,
                111L,
                111L
        ));
    }

    @Test
    void fourthReactiveShockUnlocksTheCompletionEcho() {
        assertFalse(DwarfFortressTracker.shouldTriggerEcho(3));
        assertTrue(DwarfFortressTracker.shouldTriggerEcho(4));
    }

    @Test
    void dimensionChangeInvalidatesTheRuntimeState() {
        assertTrue(DwarfFortressTracker.isSameDimension(
                Level.OVERWORLD,
                Level.OVERWORLD
        ));
        assertFalse(DwarfFortressTracker.isSameDimension(
                Level.OVERWORLD,
                Level.NETHER
        ));
    }

    @Test
    void knockbackGuardUsesTheAuthoredFullResistanceBonus() {
        assertEquals(
                1.0D,
                DwarfFortressTracker.knockbackResistanceBonus()
        );
    }

    @Test
    void removingAnOfflinePlayerWithoutStateIsIdempotent() {
        assertDoesNotThrow(() ->
                DwarfFortressTracker.removePlayer(UUID.randomUUID())
        );
    }
}
