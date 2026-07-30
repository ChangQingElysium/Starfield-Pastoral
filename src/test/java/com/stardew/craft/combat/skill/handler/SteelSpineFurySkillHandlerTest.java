package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.item.weapon.WeaponData;
import com.stardew.craft.item.weapon.WeaponRegistry;
import com.stardew.craft.item.weapon.WeaponSkillData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SteelSpineFurySkillHandlerTest {
    @Test
    void preservesTheAuthoredStanceAndCooldownContract() {
        WeaponData ironEdge = WeaponRegistry.get("iron_edge");
        assertNotNull(ironEdge);
        WeaponSkillData skill = ironEdge.getSkill1();
        assertNotNull(skill);

        assertEquals("steel_spine_fury", skill.getId());
        assertEquals(100, skill.getDamagePercent());
        assertEquals(8, skill.getCooldown());
        assertEquals(80, SteelSpineFurySkillHandler.STANCE_DURATION_TICKS);
        assertFalse(new SteelSpineFurySkillHandler().completesImmediately());
    }
}
