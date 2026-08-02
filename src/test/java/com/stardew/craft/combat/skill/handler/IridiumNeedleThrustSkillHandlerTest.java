package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.IridiumNeedleCritTracker;
import com.stardew.craft.item.weapon.WeaponData;
import com.stardew.craft.item.weapon.WeaponRegistry;
import com.stardew.craft.item.weapon.WeaponSkillData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IridiumNeedleThrustSkillHandlerTest {
    @Test
    void preservesTheAuthoredThreeStrikeContract() {
        WeaponData iridiumNeedle = WeaponRegistry.get("iridium_needle");
        assertNotNull(iridiumNeedle);
        WeaponSkillData skill = iridiumNeedle.getSkill1();
        assertNotNull(skill);

        assertEquals("iridium_needle_thrust", skill.getId());
        assertEquals(40, skill.getDamagePercent());
        assertEquals(7, skill.getCooldown());
        assertEquals(2.5, IridiumNeedleThrustSkillHandler.INITIAL_TARGET_RANGE);
        assertEquals(18, IridiumNeedleThrustSkillHandler.ANIMATION_TICKS);
        assertEquals(3, IridiumNeedleThrustSkillHandler.STRIKE_COUNT);
        assertEquals(3, IridiumNeedleThrustSkillHandler.STRIKE_INTERVAL_TICKS);
        assertEquals(2.5, IridiumNeedleThrustSkillHandler.RETARGET_RANGE);
        assertEquals(5, IridiumNeedleThrustSkillHandler.HIT_CONTEXT_LIFETIME_TICKS);
        assertEquals(3, IridiumNeedleCritTracker.MAX_STACKS);
        assertEquals(2, IridiumNeedleCritTracker.GUARANTEED_CRIT_THRESHOLD);
        assertFalse(
                new IridiumNeedleThrustSkillHandler().completesImmediately()
        );
    }

    @Test
    void finalStrikeKeepsTheAuthoredGuaranteedCriticalCadence() {
        assertEquals(
                103L,
                IridiumNeedleThrustExecutionState.nextStrikeTick(100L)
        );
        assertFalse(
                IridiumNeedleThrustExecutionState
                        .isGuaranteedCritStrike(3)
        );
        assertFalse(
                IridiumNeedleThrustExecutionState
                        .isGuaranteedCritStrike(2)
        );
        assertTrue(
                IridiumNeedleThrustExecutionState
                        .isGuaranteedCritStrike(1)
        );
    }
}
