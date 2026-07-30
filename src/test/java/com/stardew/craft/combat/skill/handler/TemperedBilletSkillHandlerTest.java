package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.item.weapon.WeaponData;
import com.stardew.craft.item.weapon.WeaponRegistry;
import com.stardew.craft.item.weapon.WeaponSkillData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemperedBilletSkillHandlerTest {
    @Test
    void preservesTheAuthoredProjectileCastContract() {
        WeaponData temperedBroadsword =
                WeaponRegistry.get("tempered_broadsword");
        assertNotNull(temperedBroadsword);
        WeaponSkillData skill = temperedBroadsword.getSkill2();
        assertNotNull(skill);

        assertEquals("tempered_billet", skill.getId());
        assertEquals(100, skill.getDamagePercent());
        assertEquals(20, skill.getCooldown());
        assertEquals(10.0F, TemperedBilletSkillHandler.ENERGY_COST);
        assertEquals(10.0, TemperedBilletSkillHandler.INITIAL_TARGET_RANGE);
        assertEquals(3, TemperedBilletSkillHandler.PROJECTILE_COUNT);
        assertEquals(1.6F, TemperedBilletSkillHandler.PROJECTILE_SPEED);
        assertEquals(0.2F, TemperedBilletSkillHandler.PROJECTILE_INACCURACY);
        assertEquals(16.0F, TemperedBilletSkillHandler.YAW_SPREAD_DEGREES);
        assertEquals(10.0F, TemperedBilletSkillHandler.PITCH_SPREAD_DEGREES);
        assertEquals(65, TemperedBilletSkillHandler.PROJECTILE_STATE_TICKS);
        assertEquals(12, TemperedBilletSkillHandler.ANIMATION_TICKS);
        assertTrue(new TemperedBilletSkillHandler().completesImmediately());
    }

    @Test
    void missingTargetsAndShortTargetListsPreserveFallbackAssignment() {
        assertEquals(-1, TemperedBilletSkillHandler.assignedTargetIndex(0, 0));
        assertEquals(0, TemperedBilletSkillHandler.assignedTargetIndex(0, 1));
        assertEquals(0, TemperedBilletSkillHandler.assignedTargetIndex(1, 1));
        assertEquals(0, TemperedBilletSkillHandler.assignedTargetIndex(2, 1));
        assertEquals(0, TemperedBilletSkillHandler.assignedTargetIndex(0, 2));
        assertEquals(1, TemperedBilletSkillHandler.assignedTargetIndex(1, 2));
        assertEquals(0, TemperedBilletSkillHandler.assignedTargetIndex(2, 2));
    }

    @Test
    void rejectedEnergyPaymentsStaySideEffectFree() {
        assertFalse(TemperedBilletSkillHandler.canPayEnergy(
                9.0F,
                false,
                false
        ));
        assertTrue(TemperedBilletSkillHandler.canPayEnergy(
                10.0F,
                false,
                false
        ));
        assertTrue(TemperedBilletSkillHandler.canPayEnergy(
                0.0F,
                true,
                false
        ));
        assertTrue(TemperedBilletSkillHandler.canPayEnergy(
                0.0F,
                false,
                true
        ));
    }
}
