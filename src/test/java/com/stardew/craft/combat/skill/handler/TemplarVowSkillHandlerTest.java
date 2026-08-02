package com.stardew.craft.combat.skill.handler;

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
        assertEquals(40, TemplarVowSkillHandler.ACTIVE_DURATION_TICKS);
        assertEquals(4.0D, TemplarVowSkillHandler.COUNTER_TARGET_RANGE);
        assertEquals(1.10F, TemplarVowSkillHandler.COUNTER_DAMAGE_MULTIPLIER);
        assertEquals(0.80F, TemplarVowSkillHandler.EXPIRE_SLASH_DAMAGE_MULTIPLIER);
        assertEquals(5, TemplarVowSkillHandler.HIT_CONTEXT_LIFETIME_TICKS);
        assertEquals(40, TemplarVowSkillHandler.EXPIRE_SHELTER_DURATION_TICKS);
        assertEquals(0, TemplarVowSkillHandler.EXPIRE_SHELTER_AMPLIFIER);
        assertEquals(40, TemplarVowSkillHandler.ANIMATION_TICKS);
        assertFalse(new TemplarVowSkillHandler().completesImmediately());
    }
}
