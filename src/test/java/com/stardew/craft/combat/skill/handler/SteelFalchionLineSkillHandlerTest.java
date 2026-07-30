package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.SteelFalchionLineTracker;
import com.stardew.craft.item.weapon.WeaponData;
import com.stardew.craft.item.weapon.WeaponRegistry;
import com.stardew.craft.item.weapon.WeaponSkillData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SteelFalchionLineSkillHandlerTest {
    @Test
    void authoredFixedLineContractRemainsStable() {
        WeaponData steelFalchion = WeaponRegistry.get("steel_falchion");
        assertNotNull(steelFalchion);
        WeaponSkillData skill = steelFalchion.getSkill1();
        assertNotNull(skill);

        assertEquals("steel_falchion_line", skill.getId());
        assertEquals(30, skill.getDamagePercent());
        assertEquals(10, skill.getCooldown());
        assertEquals(7.0D, SteelFalchionLineSkillHandler.TARGET_RANGE);
        assertEquals(
                0.30F,
                SteelFalchionLineSkillHandler.DOT_DAMAGE_MULTIPLIER
        );
        assertEquals(8, SteelFalchionLineSkillHandler.ANIMATION_TICKS);
        assertEquals(7.0F, SteelFalchionLineTracker.LINE_LENGTH);
        assertEquals(100, SteelFalchionLineTracker.LINE_DURATION_TICKS);
        assertEquals(1, SteelFalchionLineTracker.LINE_SPEED_AMPLIFIER);
        assertFalse(
                new SteelFalchionLineSkillHandler().completesImmediately()
        );
    }
}
