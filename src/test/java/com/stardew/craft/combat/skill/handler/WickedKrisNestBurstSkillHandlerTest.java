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

class WickedKrisNestBurstSkillHandlerTest {
    @Test
    void initialHitPreservesTheAuthoredHeavyStrikeContract() {
        WeaponSkillData skill = nestBurst();
        SkillContext hit = WickedKrisNestBurstSkillHandler.createHitContext(skill);

        assertEquals("wicked_kris_nest_burst", hit.getSkillId());
        assertEquals(SkillContext.SkillTier.MAJOR, hit.getTier());
        assertEquals(1.40F, hit.getDamageMultiplier());
        assertEquals(0.0F, hit.getCritChanceBonus());
        assertFalse(hit.isGuaranteedCrit());
        assertFalse(hit.isIgnoreDefense());
    }

    @Test
    void energyTargetPoisonDetonationAndLifecycleRemainUnchanged() {
        WeaponSkillData skill = nestBurst();

        assertEquals(18, skill.getCooldown());
        assertEquals(4.0, WickedKrisNestBurstSkillHandler.TARGET_RANGE);
        assertEquals(10.0F, WickedKrisNestBurstSkillHandler.ENERGY_COST);
        assertEquals(5, WickedKrisNestBurstSkillHandler.HIT_CONTEXT_LIFETIME_TICKS);
        assertEquals(200, WickedKrisNestBurstSkillHandler.POISON_DURATION_TICKS);
        assertEquals(5, WickedKrisNestBurstSkillHandler.POISON_STACKS);
        assertEquals(
                WickedKrisPoisonTracker.MAX_STACKS,
                WickedKrisNestBurstSkillHandler.POISON_STACKS
        );
        assertTrue(WickedKrisNestBurstSkillHandler.SCHEDULE_DETONATION);
        assertEquals(60, WickedKrisPoisonTracker.DETONATE_DELAY_TICKS);
        assertEquals(3.5F, WickedKrisPoisonTracker.DETONATE_RADIUS);
        assertEquals(8, WickedKrisNestBurstSkillHandler.ANIMATION_TICKS);
        assertTrue(new WickedKrisNestBurstSkillHandler().completesImmediately());
    }

    @Test
    void energyValidationHonorsCreativeAndTheFreeEnergyBlessing() {
        assertFalse(WickedKrisNestBurstSkillHandler.canPayEnergy(
                9.99F,
                false,
                false
        ));
        assertTrue(WickedKrisNestBurstSkillHandler.canPayEnergy(
                10.0F,
                false,
                false
        ));
        assertTrue(WickedKrisNestBurstSkillHandler.canPayEnergy(
                0.0F,
                true,
                false
        ));
        assertTrue(WickedKrisNestBurstSkillHandler.canPayEnergy(
                0.0F,
                false,
                true
        ));
    }

    private static WeaponSkillData nestBurst() {
        WeaponData wickedKris = WeaponRegistry.get("wicked_kris");
        assertNotNull(wickedKris);
        WeaponSkillData skill = wickedKris.getSkill2();
        assertNotNull(skill);
        assertEquals("wicked_kris_nest_burst", skill.getId());
        assertEquals(140, skill.getDamagePercent());
        return skill;
    }
}
