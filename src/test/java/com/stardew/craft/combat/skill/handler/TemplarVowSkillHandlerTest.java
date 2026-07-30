package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.TemplarVowTracker;
import com.stardew.craft.item.weapon.WeaponData;
import com.stardew.craft.item.weapon.WeaponRegistry;
import com.stardew.craft.item.weapon.WeaponSkillData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TemplarVowSkillHandlerTest {
    @Test
    void vowDataWindowAndDelayedCooldownRemainStable() {
        WeaponData templarBlade = WeaponRegistry.get("templars_blade");
        assertNotNull(templarBlade);
        WeaponSkillData skill = templarBlade.getSkill1();
        assertNotNull(skill);

        assertEquals("templar_vow", skill.getId());
        assertEquals(110, skill.getDamagePercent());
        assertEquals(6, skill.getCooldown());
        assertEquals(40, TemplarVowTracker.ACTIVE_DURATION_TICKS);
        assertEquals(40, TemplarVowSkillHandler.ANIMATION_TICKS);
        assertFalse(new TemplarVowSkillHandler().completesImmediately());
    }
}
