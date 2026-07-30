package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.IridiumNeedleCritTracker;
import com.stardew.craft.combat.skill.IridiumNeedleThrustTracker;
import com.stardew.craft.item.weapon.WeaponData;
import com.stardew.craft.item.weapon.WeaponRegistry;
import com.stardew.craft.item.weapon.WeaponSkillData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
        assertEquals(3, IridiumNeedleThrustTracker.STRIKE_COUNT);
        assertEquals(3, IridiumNeedleThrustTracker.STRIKE_INTERVAL_TICKS);
        assertEquals(2.5, IridiumNeedleThrustTracker.RETARGET_RANGE);
        assertEquals(5, IridiumNeedleThrustTracker.HIT_CONTEXT_LIFETIME_TICKS);
        assertEquals(3, IridiumNeedleCritTracker.MAX_STACKS);
        assertEquals(2, IridiumNeedleCritTracker.GUARANTEED_CRIT_THRESHOLD);
        assertFalse(
                new IridiumNeedleThrustSkillHandler().completesImmediately()
        );
    }
}
