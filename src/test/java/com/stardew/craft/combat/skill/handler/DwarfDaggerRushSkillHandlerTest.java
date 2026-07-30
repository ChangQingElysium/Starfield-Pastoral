package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.DwarfDaggerRushTracker;
import com.stardew.craft.item.weapon.WeaponData;
import com.stardew.craft.item.weapon.WeaponRegistry;
import com.stardew.craft.item.weapon.WeaponSkillData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DwarfDaggerRushSkillHandlerTest {
    @Test
    void preservesTheAuthoredRushAndThrustRefreshWindow() {
        WeaponData dwarfDagger = WeaponRegistry.get("dwarf_dagger");
        assertNotNull(dwarfDagger);
        WeaponSkillData skill = dwarfDagger.getSkill2();
        assertNotNull(skill);

        assertEquals("dwarf_dagger_rush", skill.getId());
        assertEquals(0, skill.getDamagePercent());
        assertEquals(15, skill.getCooldown());
        assertEquals(100, DwarfDaggerRushSkillHandler.ACTIVE_DURATION_TICKS);
        assertEquals(0, DwarfDaggerRushSkillHandler.SPEED_AMPLIFIER);
        assertEquals(8, DwarfDaggerRushSkillHandler.ANIMATION_TICKS);
        assertEquals(0, DwarfDaggerRushTracker.THRUST_COOLDOWN_REFRESH_TICKS);
        assertFalse(new DwarfDaggerRushSkillHandler().completesImmediately());
    }
}
