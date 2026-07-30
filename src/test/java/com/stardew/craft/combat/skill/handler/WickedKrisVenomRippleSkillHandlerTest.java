package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.combat.skill.WickedKrisPoisonTracker;
import com.stardew.craft.item.weapon.WeaponData;
import com.stardew.craft.item.weapon.WeaponRegistry;
import com.stardew.craft.item.weapon.WeaponSkillData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WickedKrisVenomRippleSkillHandlerTest {
    @Test
    void hitContextPreservesTheAuthoredAreaStrikeContract() {
        WeaponSkillData skill = venomRipple();
        SkillContext hit = WickedKrisVenomRippleSkillHandler.createHitContext(skill);

        assertEquals("wicked_kris_venom_ripple", hit.getSkillId());
        assertEquals(SkillContext.SkillTier.MINOR, hit.getTier());
        assertEquals(0.60F, hit.getDamageMultiplier());
        assertEquals(0.0F, hit.getCritChanceBonus());
        assertFalse(hit.isGuaranteedCrit());
        assertFalse(hit.isIgnoreDefense());
    }

    @Test
    void poisonSpeedTargetingAndLifecycleRemainUnchanged() {
        WeaponSkillData skill = venomRipple();

        assertEquals(7, skill.getCooldown());
        assertEquals(4.0, WickedKrisVenomRippleSkillHandler.TARGET_RADIUS);
        assertEquals(5, WickedKrisVenomRippleSkillHandler.HIT_CONTEXT_LIFETIME_TICKS);
        assertEquals(100, WickedKrisVenomRippleSkillHandler.POISON_DURATION_TICKS);
        assertEquals(5, WickedKrisVenomRippleSkillHandler.POISON_STACKS);
        assertEquals(
                WickedKrisPoisonTracker.MAX_STACKS,
                WickedKrisVenomRippleSkillHandler.POISON_STACKS
        );
        assertFalse(WickedKrisVenomRippleSkillHandler.SCHEDULE_DETONATION);
        assertEquals(40, WickedKrisVenomRippleSkillHandler.SPEED_DURATION_TICKS);
        assertEquals(0, WickedKrisVenomRippleSkillHandler.SPEED_AMPLIFIER);
        assertTrue(new WickedKrisVenomRippleSkillHandler().completesImmediately());
    }

    private static WeaponSkillData venomRipple() {
        WeaponData wickedKris = WeaponRegistry.get("wicked_kris");
        assertNotNull(wickedKris);
        WeaponSkillData skill = wickedKris.getSkill1();
        assertNotNull(skill);
        assertEquals("wicked_kris_venom_ripple", skill.getId());
        assertEquals(60, skill.getDamagePercent());
        return skill;
    }
}
