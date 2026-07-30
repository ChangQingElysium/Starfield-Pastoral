package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.CarvingKnifeThrustTracker;
import com.stardew.craft.item.weapon.WeaponData;
import com.stardew.craft.item.weapon.WeaponRegistry;
import com.stardew.craft.item.weapon.WeaponSkillData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CarvingThrustSkillHandlerTest {
    @Test
    void preservesTheAuthoredMultiStrikeContract() {
        WeaponData carvingKnife = WeaponRegistry.get("carving_knife");
        assertNotNull(carvingKnife);
        WeaponSkillData skill = carvingKnife.getSkill1();
        assertNotNull(skill);

        assertEquals("carving_thrust", skill.getId());
        assertEquals(45, skill.getDamagePercent());
        assertEquals(5, skill.getCooldown());
        assertEquals(2.5, CarvingThrustSkillHandler.INITIAL_TARGET_RANGE);
        assertEquals(5, CarvingThrustSkillHandler.DAMAGE_RESISTANCE_TICKS);
        assertEquals(0, CarvingThrustSkillHandler.DAMAGE_RESISTANCE_AMPLIFIER);
        assertEquals(18, CarvingThrustSkillHandler.ANIMATION_TICKS);
        assertEquals(3, CarvingKnifeThrustTracker.DEFAULT_STRIKES);
        assertEquals(3, CarvingKnifeThrustTracker.DEFAULT_INTERVAL_TICKS);
        assertEquals(0.45F, CarvingKnifeThrustTracker.BASE_DAMAGE_MULTIPLIER);
        assertEquals(0.60F, CarvingKnifeThrustTracker.BONUS_DAMAGE_MULTIPLIER);
        assertEquals(2, CarvingKnifeThrustTracker.BONUS_DELAY_TICKS);
        assertEquals(5, CarvingKnifeThrustTracker.HIT_CONTEXT_LIFETIME_TICKS);
        assertEquals(2.5, CarvingKnifeThrustTracker.REACQUIRE_RANGE);
        assertFalse(new CarvingThrustSkillHandler().completesImmediately());
    }
}
