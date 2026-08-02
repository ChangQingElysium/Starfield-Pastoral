package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.item.weapon.WeaponData;
import com.stardew.craft.item.weapon.WeaponRegistry;
import com.stardew.craft.item.weapon.WeaponSkillData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DwarfDaggerThrustSkillHandlerTest {
    @Test
    void preservesTheAuthoredPathHitAndOneTimeRewardContract() {
        WeaponData dwarfDagger = WeaponRegistry.get("dwarf_dagger");
        assertNotNull(dwarfDagger);
        WeaponSkillData skill = dwarfDagger.getSkill1();
        assertNotNull(skill);

        assertEquals("dwarf_dagger_thrust", skill.getId());
        assertEquals(120, skill.getDamagePercent());
        assertEquals(7, skill.getCooldown());
        assertEquals(8.0, DwarfDaggerThrustSkillHandler.THRUST_DISTANCE);
        assertEquals(5, DwarfDaggerThrustSkillHandler.THRUST_DURATION_TICKS);
        assertEquals(8, DwarfDaggerThrustSkillHandler.ANIMATION_TICKS);
        assertEquals(1.2, DwarfDaggerThrustSkillHandler.HIT_RADIUS);
        assertEquals(
                5,
                DwarfDaggerThrustSkillHandler.HIT_CONTEXT_LIFETIME_TICKS
        );
        assertEquals(
                100,
                DwarfDaggerThrustSkillHandler.WEAK_POINT_DURATION_TICKS
        );
        assertEquals(3, DwarfDaggerThrustSkillHandler.WEAK_POINT_AMPLIFIER);
        assertEquals(
                50,
                DwarfDaggerThrustSkillHandler.RESISTANCE_DURATION_TICKS
        );
        assertEquals(2, DwarfDaggerThrustSkillHandler.RESISTANCE_AMPLIFIER);
        assertEquals(2.0F, DwarfDaggerThrustSkillHandler.ENERGY_RESTORE);
        assertFalse(new DwarfDaggerThrustSkillHandler().completesImmediately());
    }

    @Test
    void executionKeepsTheInclusiveFinalMovementTick() {
        long endTick = 105L;

        assertTrue(DwarfDaggerThrustExecutionState.isWithinExecutionWindow(
                endTick,
                endTick
        ));
        assertFalse(DwarfDaggerThrustExecutionState.isWithinExecutionWindow(
                endTick + 1L,
                endTick
        ));
        assertFalse(DwarfDaggerThrustExecutionState.shouldSnapToEnd(
                endTick - 2L,
                endTick
        ));
        assertTrue(DwarfDaggerThrustExecutionState.shouldSnapToEnd(
                endTick - 1L,
                endTick
        ));
    }

    @Test
    void firstPositiveAppliedHitOwnsTheOneTimeReward() {
        assertTrue(DwarfDaggerThrustExecutionState.shouldApplyHitBonus(
                true,
                false
        ));
        assertFalse(DwarfDaggerThrustExecutionState.shouldApplyHitBonus(
                false,
                false
        ));
        assertFalse(DwarfDaggerThrustExecutionState.shouldApplyHitBonus(
                true,
                true
        ));
    }
}
