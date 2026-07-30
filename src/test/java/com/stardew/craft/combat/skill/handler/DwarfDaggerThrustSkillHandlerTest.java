package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.DwarfDaggerThrustTracker;
import com.stardew.craft.item.weapon.WeaponData;
import com.stardew.craft.item.weapon.WeaponRegistry;
import com.stardew.craft.item.weapon.WeaponSkillData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
        assertEquals(1.2, DwarfDaggerThrustTracker.HIT_RADIUS);
        assertEquals(
                5,
                DwarfDaggerThrustTracker.HIT_CONTEXT_LIFETIME_TICKS
        );
        assertEquals(
                100,
                DwarfDaggerThrustTracker.WEAK_POINT_DURATION_TICKS
        );
        assertEquals(3, DwarfDaggerThrustTracker.WEAK_POINT_AMPLIFIER);
        assertEquals(
                50,
                DwarfDaggerThrustTracker.RESISTANCE_DURATION_TICKS
        );
        assertEquals(2, DwarfDaggerThrustTracker.RESISTANCE_AMPLIFIER);
        assertEquals(2.0F, DwarfDaggerThrustTracker.ENERGY_RESTORE);
        assertFalse(new DwarfDaggerThrustSkillHandler().completesImmediately());
    }
}
