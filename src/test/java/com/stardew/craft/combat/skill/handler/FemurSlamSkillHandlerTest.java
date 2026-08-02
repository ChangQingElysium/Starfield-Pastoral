package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.item.weapon.WeaponData;
import com.stardew.craft.item.weapon.WeaponRegistry;
import com.stardew.craft.item.weapon.WeaponSkillData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FemurSlamSkillHandlerTest {
    @Test
    void authoredChargeAndSlamContractRemainStable() {
        WeaponData femur = WeaponRegistry.get("femur");
        assertNotNull(femur);
        WeaponSkillData skill = femur.getSkill1();
        assertNotNull(skill);

        assertEquals("femur_slam", skill.getId());
        assertEquals(120, skill.getDamagePercent());
        assertEquals(7, skill.getCooldown());
        assertEquals(20, FemurSlamSkillHandler.CHARGE_TICKS);
        assertEquals(3.0D, FemurSlamSkillHandler.RANGE);
        assertEquals(0.5D, FemurSlamSkillHandler.MIN_DOT);
        assertEquals(40, FemurSlamSkillHandler.SLOW_TICKS);
        assertEquals(0, FemurSlamSkillHandler.SLOW_AMPLIFIER);
        assertEquals(4, FemurSlamSkillHandler.STAGGER_TICKS);
        assertEquals(0, FemurSlamSkillHandler.STAGGER_AMPLIFIER);
        assertEquals(0.7F, FemurSlamSkillHandler.KNOCKBACK_MULTI);
        assertEquals(1.1F, FemurSlamSkillHandler.KNOCKBACK_SINGLE);
        assertEquals(28, FemurSlamSkillHandler.QUAKE_TREMOR_MAX);
        assertEquals(
                5,
                FemurSlamSkillHandler.HIT_CONTEXT_LIFETIME_TICKS
        );
        assertFalse(new FemurSlamSkillHandler().completesImmediately());
    }
}
