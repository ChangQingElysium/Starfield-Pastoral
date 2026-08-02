package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.item.weapon.WeaponData;
import com.stardew.craft.item.weapon.WeaponRegistry;
import com.stardew.craft.item.weapon.WeaponSkillData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class InsectEyeStanceSkillHandlerTest {
    @Test
    void stanceDataDurationNotificationAndLifecycleRemainUnchanged() {
        WeaponData insectHead = WeaponRegistry.get("insect_head");
        assertNotNull(insectHead);
        WeaponSkillData skill = insectHead.getSkill1();
        assertNotNull(skill);

        assertEquals("insect_eye_stance", skill.getId());
        assertEquals(105, skill.getDamagePercent());
        assertEquals(8, skill.getCooldown());
        assertEquals(
                1.05F,
                InsectEyeStanceSkillHandler.DAMAGE_MULTIPLIER
        );
        assertEquals(30, InsectEyeStanceSkillHandler.ACTIVE_DURATION_TICKS);
        assertEquals(1, InsectEyeStanceSkillHandler.ANIMATION_TICKS);
        assertFalse(new InsectEyeStanceSkillHandler().completesImmediately());
    }
}
