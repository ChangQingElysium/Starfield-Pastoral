package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.DragonBreathTracker;
import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.item.weapon.WeaponData;
import com.stardew.craft.item.weapon.WeaponRegistry;
import com.stardew.craft.item.weapon.WeaponSkillData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DragonBreathJudgementSkillHandlerTest {
    @Test
    void preservesTheAuthoredMajorDamageArcAndPresentationContract() {
        WeaponData cutlass = WeaponRegistry.get("dragontooth_cutlass");
        assertNotNull(cutlass);
        WeaponSkillData skill = cutlass.getSkill2();
        assertNotNull(skill);

        SkillContext context =
                DragonBreathJudgementSkillHandler.createHitContext(
                        skill,
                        0.20F
                );

        assertEquals("dragon_breath_judgement", skill.getId());
        assertEquals(260, skill.getDamagePercent());
        assertEquals(0, skill.getCooldown());
        assertEquals(SkillContext.SkillTier.MAJOR, context.getTier());
        assertEquals(2.6F, context.getDamageMultiplier());
        assertEquals(0.20F, context.getCritChanceBonus());
        assertFalse(context.isGuaranteedCrit());
        assertFalse(context.isIgnoreDefense());

        assertEquals(4.0D, DragonBreathJudgementSkillHandler.TARGET_RANGE);
        assertEquals(
                0.5D,
                DragonBreathJudgementSkillHandler.MINIMUM_TARGET_DOT
        );
        assertEquals(
                5,
                DragonBreathJudgementSkillHandler.MAXIMUM_STACK_REFUND
        );
        assertEquals(
                5,
                DragonBreathJudgementSkillHandler
                        .HIT_CONTEXT_LIFETIME_TICKS
        );
        assertEquals(8, DragonBreathJudgementSkillHandler.ANIMATION_TICKS);
        assertTrue(
                new DragonBreathJudgementSkillHandler()
                        .completesImmediately()
        );
    }

    @Test
    void onlyStacksAboveFifteenBecomeCriticalChance() {
        assertEquals(
                0,
                DragonBreathJudgementSkillHandler.extraStacks(15)
        );
        assertEquals(
                5,
                DragonBreathJudgementSkillHandler.extraStacks(20)
        );
        assertEquals(
                0.0F,
                DragonBreathJudgementSkillHandler
                        .criticalChanceBonus(15)
        );
        assertEquals(
                0.04F,
                DragonBreathJudgementSkillHandler
                        .criticalChanceBonus(16)
        );
        assertEquals(
                0.20F,
                DragonBreathJudgementSkillHandler
                        .criticalChanceBonus(20),
                1.0E-6F
        );
        assertEquals(15, DragonBreathTracker.MAJOR_THRESHOLD);
        assertEquals(20, DragonBreathTracker.MAX_STACKS);
    }

    @Test
    void refundUsesTargetsInTheArcAndCapsAtFive() {
        assertEquals(
                0,
                DragonBreathJudgementSkillHandler
                        .refundForTargetCount(0)
        );
        assertEquals(
                3,
                DragonBreathJudgementSkillHandler
                        .refundForTargetCount(3)
        );
        assertEquals(
                5,
                DragonBreathJudgementSkillHandler
                        .refundForTargetCount(8)
        );
    }
}
