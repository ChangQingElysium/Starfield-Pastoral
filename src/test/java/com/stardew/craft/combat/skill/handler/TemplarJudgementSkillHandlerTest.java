package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.item.weapon.WeaponData;
import com.stardew.craft.item.weapon.WeaponRegistry;
import com.stardew.craft.item.weapon.WeaponSkillData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemplarJudgementSkillHandlerTest {
    @Test
    void authoredSkillDataAndRuntimeCostsRemainStable() {
        WeaponData templarBlade = WeaponRegistry.get("templars_blade");
        assertNotNull(templarBlade);
        WeaponSkillData skill = templarBlade.getSkill2();
        assertNotNull(skill);

        assertEquals("templar_judgement", skill.getId());
        assertEquals(160, skill.getDamagePercent());
        assertEquals(20, skill.getCooldown());
        assertEquals(6.0D, TemplarJudgementSkillHandler.TARGET_RADIUS);
        assertEquals(10.0F, TemplarJudgementSkillHandler.ENERGY_COST);
        assertEquals(100, TemplarJudgementSkillHandler.DURATION_TICKS);
        assertEquals(0.35F, TemplarJudgementSkillHandler.SHARE_RATIO);
        assertEquals(
                0.25F,
                TemplarJudgementSkillHandler
                        .MAX_HEALTH_DAMAGE_CAP_RATIO
        );
        assertEquals(
                1.6F,
                TemplarJudgementSkillHandler
                        .SETTLEMENT_DAMAGE_MULTIPLIER
        );
        assertEquals(
                5,
                TemplarJudgementSkillHandler
                        .HIT_CONTEXT_LIFETIME_TICKS
        );
        assertFalse(new TemplarJudgementSkillHandler().completesImmediately());
    }

    @Test
    void energyPreflightHonorsCreativeAndFreeEnergyRules() {
        assertFalse(TemplarJudgementSkillHandler.canPayEnergy(
                9.99F,
                false,
                false
        ));
        assertTrue(TemplarJudgementSkillHandler.canPayEnergy(
                10.0F,
                false,
                false
        ));
        assertTrue(TemplarJudgementSkillHandler.canPayEnergy(
                0.0F,
                true,
                false
        ));
        assertTrue(TemplarJudgementSkillHandler.canPayEnergy(
                0.0F,
                false,
                true
        ));
    }

    @Test
    void sharedDamagePreservesAuthoredRatioAndHealthCap() {
        assertEquals(
                35.0F,
                TemplarJudgementSkillHandler.cappedSharedDamage(
                        100.0F,
                        200.0F
                )
        );
        assertEquals(
                50.0F,
                TemplarJudgementSkillHandler.cappedSharedDamage(
                        200.0F,
                        200.0F
                )
        );
    }
}
