package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.LightCounterParryState;
import com.stardew.craft.item.weapon.WeaponData;
import com.stardew.craft.item.weapon.WeaponRegistry;
import com.stardew.craft.item.weapon.WeaponSkillData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LightCounterSkillHandlerTest {
    @Test
    void preservesTheAuthoredCounterContract() {
        WeaponData steelSmallsword = WeaponRegistry.get("steel_smallsword");
        assertNotNull(steelSmallsword);
        WeaponSkillData skill = steelSmallsword.getSkill1();
        assertNotNull(skill);

        assertEquals("light_counter", skill.getId());
        assertEquals(120, skill.getDamagePercent());
        assertEquals(6, skill.getCooldown());
        assertEquals(
                LightCounterParryState.DEFAULT_WINDOW_TICKS,
                LightCounterSkillHandler.WINDOW_TICKS
        );
        assertEquals(20, LightCounterSkillHandler.WINDOW_TICKS);
        assertEquals(7, LightCounterSkillHandler.INITIAL_RESISTANCE_TICKS);
        assertEquals(0, LightCounterSkillHandler.INITIAL_RESISTANCE_AMPLIFIER);
        assertEquals(20, LightCounterSkillHandler.ANIMATION_TICKS);
        assertEquals(8, LightCounterParryState.COUNTER_ANIM_TICKS);
        assertFalse(new LightCounterSkillHandler().completesImmediately());
    }
}
