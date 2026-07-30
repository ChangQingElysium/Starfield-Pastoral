package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.OssifiedExecutionTracker;
import com.stardew.craft.item.weapon.WeaponData;
import com.stardew.craft.item.weapon.WeaponRegistry;
import com.stardew.craft.item.weapon.WeaponSkillData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OssifiedExecutionSkillHandlerTest {
    @Test
    void preservesTheAuthoredExecutionCircleContract() {
        WeaponData ossifiedBlade = WeaponRegistry.get("ossified_blade");
        assertNotNull(ossifiedBlade);
        WeaponSkillData skill = ossifiedBlade.getSkill2();
        assertNotNull(skill);

        assertEquals("ossified_execution", skill.getId());
        assertEquals(50, skill.getDamagePercent());
        assertEquals(18, skill.getCooldown());
        assertEquals(6.0, OssifiedExecutionSkillHandler.TARGET_RANGE);
        assertEquals(10.0F, OssifiedExecutionSkillHandler.ENERGY_COST);
        assertEquals(4.0F, OssifiedExecutionSkillHandler.CIRCLE_RADIUS);
        assertEquals(60, OssifiedExecutionSkillHandler.DURATION_TICKS);
        assertEquals(20, OssifiedExecutionTracker.DAMAGE_INTERVAL_TICKS);
        assertEquals(5, OssifiedExecutionTracker.HIT_CONTEXT_LIFETIME_TICKS);
        assertEquals(0.20F, OssifiedExecutionTracker.CRIT_DAMAGE_BONUS);
        assertEquals(5, OssifiedExecutionTracker.RING_PARTICLE_INTERVAL_TICKS);
        assertEquals(0.01, OssifiedExecutionTracker.MINIMUM_PULL_DISTANCE);
        assertEquals(0.02, OssifiedExecutionTracker.BASE_PULL_STRENGTH);
        assertEquals(0.03, OssifiedExecutionTracker.INNER_PULL_BONUS);
        assertEquals(
                3,
                OssifiedExecutionSkillHandler.DURATION_TICKS
                        / OssifiedExecutionTracker.DAMAGE_INTERVAL_TICKS
        );
        assertFalse(new OssifiedExecutionSkillHandler().completesImmediately());
    }

    @Test
    void energyValidationIsSideEffectFreeAndHonorsFreeCastModes() {
        assertTrue(OssifiedExecutionSkillHandler.canPayEnergy(
                10.0F,
                false,
                false
        ));
        assertFalse(OssifiedExecutionSkillHandler.canPayEnergy(
                9.99F,
                false,
                false
        ));
        assertTrue(OssifiedExecutionSkillHandler.canPayEnergy(
                0.0F,
                true,
                false
        ));
        assertTrue(OssifiedExecutionSkillHandler.canPayEnergy(
                0.0F,
                false,
                true
        ));
    }
}
