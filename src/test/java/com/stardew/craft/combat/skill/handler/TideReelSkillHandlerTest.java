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

class TideReelSkillHandlerTest {
    @Test
    void hitContextsPreserveTheAuthoredDamageAndFishCatchContract() {
        WeaponSkillData skill = tideReel();

        SkillContext normal = TideReelSkillHandler.createHitContext(skill, false);
        SkillContext fishCatch = TideReelSkillHandler.createHitContext(skill, true);

        assertEquals("tide_reel", normal.getSkillId());
        assertEquals(SkillContext.SkillTier.MAJOR, normal.getTier());
        assertEquals(2.0F, normal.getDamageMultiplier());
        assertEquals(2.6F, fishCatch.getDamageMultiplier());
        assertEquals(0.0F, normal.getCritChanceBonus());
        assertFalse(normal.isGuaranteedCrit());
        assertFalse(normal.isIgnoreDefense());
    }

    @Test
    void cooldownEnergyAndPullRulesRemainUnchanged() {
        WeaponSkillData skill = tideReel();
        int baseCooldownTicks = skill.getCooldown() * 20;

        assertEquals(18, skill.getCooldown());
        assertEquals(360, TideReelSkillHandler.appliedCooldownTicks(
                baseCooldownTicks,
                false
        ));
        assertEquals(320, TideReelSkillHandler.appliedCooldownTicks(
                baseCooldownTicks,
                true
        ));
        assertEquals(1, TideReelSkillHandler.appliedCooldownTicks(20, true));

        assertFalse(TideReelSkillHandler.canPayEnergy(9.99F, false, false));
        assertTrue(TideReelSkillHandler.canPayEnergy(10.0F, false, false));
        assertTrue(TideReelSkillHandler.canPayEnergy(0.0F, true, false));
        assertTrue(TideReelSkillHandler.canPayEnergy(0.0F, false, true));

        assertEquals(0.40, TideReelSkillHandler.pullStrength(false));
        assertEquals(0.55, TideReelSkillHandler.pullStrength(true));
        assertEquals(0.08, TideReelSkillHandler.pullLift(false));
        assertEquals(0.12, TideReelSkillHandler.pullLift(true));
    }

    @Test
    void targetingPresentationAndImmediateLifecycleRemainUnchanged() {
        assertEquals(4.0, TideReelSkillHandler.TARGET_RANGE);
        assertEquals(10.0F, TideReelSkillHandler.ENERGY_COST);
        assertEquals(0.60F, TideReelSkillHandler.FISH_CATCH_DAMAGE_BONUS);
        assertEquals(40, TideReelSkillHandler.FISH_INVENTORY_COOLDOWN_REDUCTION_TICKS);
        assertEquals(5, TideReelSkillHandler.HIT_CONTEXT_LIFETIME_TICKS);
        assertEquals(100, TideReelSkillHandler.FISH_CATCH_SLOW_TICKS);
        assertEquals(0, TideReelSkillHandler.FISH_CATCH_SLOW_AMPLIFIER);
        assertEquals(0.01, TideReelSkillHandler.MINIMUM_PULL_DISTANCE_SQUARED);
        assertEquals(4.6F, TideReelSkillHandler.WATER_RING_RADIUS);
        assertEquals(24, TideReelSkillHandler.WATER_RING_DURATION_TICKS);
        assertEquals(12, TideReelSkillHandler.ANIMATION_TICKS);
        assertTrue(new TideReelSkillHandler().completesImmediately());
    }

    private static WeaponSkillData tideReel() {
        WeaponData brokenTrident = WeaponRegistry.get("broken_trident");
        assertNotNull(brokenTrident);
        WeaponSkillData skill = brokenTrident.getSkill2();
        assertNotNull(skill);
        assertEquals("tide_reel", skill.getId());
        assertEquals(200, skill.getDamagePercent());
        return skill;
    }
}
