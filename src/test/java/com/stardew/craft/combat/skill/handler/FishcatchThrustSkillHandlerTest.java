package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.item.weapon.WeaponData;
import com.stardew.craft.item.weapon.WeaponRegistry;
import com.stardew.craft.item.weapon.WeaponSkillData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FishcatchThrustSkillHandlerTest {
    @Test
    void preservesTheAuthoredTripleThrustAndFishCatchContract() {
        WeaponData brokenTrident = WeaponRegistry.get("broken_trident");
        assertNotNull(brokenTrident);
        WeaponSkillData skill = brokenTrident.getSkill1();
        assertNotNull(skill);

        assertEquals("fishcatch_thrust", skill.getId());
        assertEquals(40, skill.getDamagePercent());
        assertEquals(6, skill.getCooldown());
        assertEquals(2.5, FishcatchThrustSkillHandler.INITIAL_TARGET_RANGE);
        assertEquals(5, FishcatchThrustSkillHandler.DAMAGE_RESISTANCE_TICKS);
        assertEquals(0, FishcatchThrustSkillHandler.DAMAGE_RESISTANCE_AMPLIFIER);
        assertEquals(18, FishcatchThrustSkillHandler.ANIMATION_TICKS);
        assertEquals(3, FishcatchThrustSkillHandler.STRIKE_COUNT);
        assertEquals(3, FishcatchThrustSkillHandler.STRIKE_INTERVAL_TICKS);
        assertEquals(100, FishcatchThrustSkillHandler.FISH_CATCH_DURATION_TICKS);
        assertEquals(0.10F, FishcatchThrustSkillHandler.FISH_CATCH_DAMAGE_BONUS);
        assertEquals(0, FishcatchThrustSkillHandler.FISH_CATCH_SLOW_AMPLIFIER);
        assertEquals(5, FishcatchThrustSkillHandler.HIT_CONTEXT_LIFETIME_TICKS);
        assertEquals(2.5, FishcatchThrustSkillHandler.REACQUIRE_RANGE);
        assertFalse(new FishcatchThrustSkillHandler().completesImmediately());
    }

    @Test
    void activeFishCatchAddsTenPercentagePointsPerStrike() {
        assertEquals(
                0.40F,
                FishcatchThrustExecutionState.damageMultiplier(
                        0.40F,
                        false
                )
        );
        assertEquals(
                0.50F,
                FishcatchThrustExecutionState.damageMultiplier(
                        0.40F,
                        true
                )
        );
    }
}
