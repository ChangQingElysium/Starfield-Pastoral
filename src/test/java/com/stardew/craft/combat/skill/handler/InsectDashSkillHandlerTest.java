package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.item.weapon.WeaponData;
import com.stardew.craft.item.weapon.WeaponRegistry;
import com.stardew.craft.item.weapon.WeaponSkillData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InsectDashSkillHandlerTest {
    @Test
    void preservesTheAuthoredThreeStageDamageAndEnergyContract() {
        WeaponData insectHead = WeaponRegistry.get("insect_head");
        assertNotNull(insectHead);
        WeaponSkillData skill = insectHead.getSkill2();
        assertNotNull(skill);

        assertEquals("insect_dash", skill.getId());
        assertEquals(80, skill.getDamagePercent());
        assertEquals(20, skill.getCooldown());
        assertEquals(3.0F, InsectDashSkillHandler.energyCostForStage(1));
        assertEquals(5.0F, InsectDashSkillHandler.energyCostForStage(2));
        assertEquals(7.0F, InsectDashSkillHandler.energyCostForStage(3));
        assertEquals(0.8F, InsectDashSkillHandler.damageMultiplierForStage(1));
        assertEquals(1.0F, InsectDashSkillHandler.damageMultiplierForStage(2));
        assertEquals(1.2F, InsectDashSkillHandler.damageMultiplierForStage(3));

        SkillContext thirdHit = InsectDashSkillHandler.createHitContext(
                skill.getId(),
                3
        );
        assertEquals(SkillContext.SkillTier.MAJOR, thirdHit.getTier());
        assertEquals(1.2F, thirdHit.getDamageMultiplier());
        assertFalse(thirdHit.isGuaranteedCrit());
        assertFalse(thirdHit.isIgnoreDefense());
    }

    @Test
    void preservesDashChainMovementAndFinisherConstants() {
        assertEquals(7.0, InsectDashSkillHandler.DASH_DISTANCE);
        assertEquals(1.2, InsectDashSkillHandler.PATH_HIT_RADIUS);
        assertEquals(2, InsectDashSkillHandler.REQUIRED_HITS_TO_CONTINUE);
        assertEquals(3, InsectDashSkillHandler.MAX_STAGE);
        assertEquals(5, InsectDashSkillHandler.DASH_DURATION_TICKS);
        assertEquals(5, InsectDashSkillHandler.HIT_CONTEXT_LIFETIME_TICKS);
        assertEquals(60, InsectDashSkillHandler.FINISH_SPEED_DURATION_TICKS);
        assertEquals(0, InsectDashSkillHandler.FINISH_SPEED_AMPLIFIER);
        assertEquals(8, InsectDashSkillHandler.ANIMATION_TICKS);

        assertTrue(InsectDashSkillHandler.continuesChain(1, 2));
        assertTrue(InsectDashSkillHandler.continuesChain(2, 2));
        assertFalse(InsectDashSkillHandler.continuesChain(1, 1));
        assertFalse(InsectDashSkillHandler.continuesChain(3, 2));
        assertTrue(new InsectDashSkillHandler().completesImmediately());
    }

    @Test
    void rejectedEnergyPaymentsStaySideEffectFree() {
        assertFalse(InsectDashSkillHandler.canPayEnergy(
                4.0F,
                false,
                false,
                2
        ));
        assertTrue(InsectDashSkillHandler.canPayEnergy(
                5.0F,
                false,
                false,
                2
        ));
        assertTrue(InsectDashSkillHandler.canPayEnergy(
                0.0F,
                true,
                false,
                3
        ));
        assertTrue(InsectDashSkillHandler.canPayEnergy(
                0.0F,
                false,
                true,
                3
        ));
    }
}
