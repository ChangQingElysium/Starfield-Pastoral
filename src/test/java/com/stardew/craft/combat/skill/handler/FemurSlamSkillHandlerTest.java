package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.FemurSlamTracker;
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
        assertEquals(20, FemurSlamTracker.CHARGE_TICKS);
        assertFalse(new FemurSlamSkillHandler().completesImmediately());
    }
}
