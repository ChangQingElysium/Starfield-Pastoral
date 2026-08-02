package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.item.weapon.WeaponData;
import com.stardew.craft.item.weapon.WeaponRegistry;
import com.stardew.craft.item.weapon.WeaponSkillData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DarkSwordBloodDebtSkillHandlerTest {
    @Test
    void preservesAuthoredHealthDamageAndLifestealContract() {
        WeaponData darkSword = WeaponRegistry.get("dark_sword");
        assertNotNull(darkSword);
        WeaponSkillData skill = darkSword.getSkill1();
        assertNotNull(skill);

        SkillContext hitContext =
                DarkSwordBloodDebtSkillHandler.createHitContext(skill);

        assertEquals("dark_sword_blood_debt", skill.getId());
        assertEquals(140, skill.getDamagePercent());
        assertEquals(7, skill.getCooldown());
        assertEquals(SkillContext.SkillTier.MINOR, hitContext.getTier());
        assertEquals(1.40F, hitContext.getDamageMultiplier());
        assertFalse(hitContext.isIgnoreDefense());
        assertEquals(
                100,
                DarkSwordBloodDebtSkillHandler.ACTIVE_DURATION_TICKS
        );
        assertEquals(
                0.20F,
                DarkSwordBloodDebtSkillHandler.LIFESTEAL_RATIO
        );
        assertEquals(4.0, DarkSwordBloodDebtSkillHandler.TARGET_RANGE);
        assertEquals(8, DarkSwordBloodDebtSkillHandler.ANIMATION_TICKS);
        assertFalse(new DarkSwordBloodDebtSkillHandler().completesImmediately());
    }

    @Test
    void currentHealthCostKeepsTheAuthoredSixPercentAndOnePointFloor() {
        assertEquals(
                1.0F,
                DarkSwordBloodDebtSkillHandler.healthCost(1.0F)
        );
        assertEquals(
                1.0F,
                DarkSwordBloodDebtSkillHandler.healthCost(10.0F)
        );
        assertEquals(
                6.0F,
                DarkSwordBloodDebtSkillHandler.healthCost(100.0F)
        );
    }

    @Test
    void cooldownCommitsAfterActivationForCompletionCancellationAndLogout() {
        assertTrue(DarkSwordBloodDebtSkillHandler.shouldCommitCooldown(
                SkillInstance.EndReason.COMPLETED,
                false
        ));
        assertTrue(DarkSwordBloodDebtSkillHandler.shouldCommitCooldown(
                SkillInstance.EndReason.INVALIDATED,
                true
        ));
        assertTrue(DarkSwordBloodDebtSkillHandler.shouldCommitCooldown(
                SkillInstance.EndReason.CASTER_UNAVAILABLE,
                true
        ));
        assertFalse(DarkSwordBloodDebtSkillHandler.shouldCommitCooldown(
                SkillInstance.EndReason.INVALIDATED,
                false
        ));
        assertFalse(DarkSwordBloodDebtSkillHandler.shouldCommitCooldown(
                SkillInstance.EndReason.CASTER_UNAVAILABLE,
                false
        ));
    }
}
