package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.combat.skill.TemperedQuenchTracker;
import com.stardew.craft.item.weapon.WeaponData;
import com.stardew.craft.item.weapon.WeaponRegistry;
import com.stardew.craft.item.weapon.WeaponSkillData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TemperedQuenchSkillHandlerTest {
    @Test
    void preservesTheAuthoredQuenchContract() {
        WeaponData temperedBroadsword = WeaponRegistry.get(
                "tempered_broadsword"
        );
        assertNotNull(temperedBroadsword);
        WeaponSkillData skill = temperedBroadsword.getSkill1();
        assertNotNull(skill);

        assertEquals("tempered_quench", skill.getId());
        assertEquals(105, skill.getDamagePercent());
        assertEquals(8, skill.getCooldown());
        assertEquals(4.5, TemperedQuenchSkillHandler.TARGET_RANGE);
        assertEquals(20, TemperedQuenchSkillHandler.BLAST_DELAY_TICKS);
        assertEquals(
                5,
                TemperedQuenchSkillHandler.HIT_CONTEXT_LIFETIME_TICKS
        );
        assertEquals(10, TemperedQuenchSkillHandler.ANIMATION_TICKS);
        assertFalse(new TemperedQuenchSkillHandler().completesImmediately());
    }

    @Test
    void initialHitUsesTheRegistryMinorDamageContext() {
        WeaponData temperedBroadsword = WeaponRegistry.get(
                "tempered_broadsword"
        );
        assertNotNull(temperedBroadsword);
        WeaponSkillData skill = temperedBroadsword.getSkill1();
        assertNotNull(skill);

        SkillContext context = TemperedQuenchSkillHandler.createHitContext(
                skill
        );

        assertEquals("tempered_quench", context.getSkillId());
        assertEquals(SkillContext.SkillTier.MINOR, context.getTier());
        assertEquals(1.05F, context.getDamageMultiplier());
        assertFalse(context.isIgnoreDefense());
        assertFalse(context.isGuaranteedCrit());
    }

    @Test
    void delayedBlastKeepsTheOriginalDamageAndDebuffValues() {
        assertEquals(0.45F, TemperedQuenchTracker.BLAST_DAMAGE_MULTIPLIER);
        assertEquals(5, TemperedQuenchTracker.HIT_CONTEXT_LIFETIME_TICKS);
        assertEquals(60, TemperedQuenchTracker.VULNERABLE_DURATION_TICKS);
        assertEquals(1, TemperedQuenchTracker.VULNERABLE_AMPLIFIER);
    }
}
