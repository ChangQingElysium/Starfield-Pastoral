package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.item.weapon.WeaponData;
import com.stardew.craft.item.weapon.WeaponRegistry;
import com.stardew.craft.item.weapon.WeaponSkillData;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InfinityDaggerSingularityBackstabSkillHandlerTest {
    @Test
    void preservesTheAuthoredTwoHitContract() {
        WeaponData infinityDagger =
                WeaponRegistry.get("infinity_dagger");
        assertNotNull(infinityDagger);
        WeaponSkillData skill = infinityDagger.getSkill2();
        assertNotNull(skill);

        SkillContext first =
                InfinityDaggerSingularityBackstabSkillHandler
                        .createHitContext(skill, false, true);
        SkillContext second =
                InfinityDaggerSingularityBackstabSkillHandler
                        .createHitContext(skill, true, false);
        SkillContext markedSecond =
                InfinityDaggerSingularityBackstabSkillHandler
                        .createHitContext(skill, true, true);

        assertEquals(
                "infinity_dagger_singularity_backstab",
                skill.getId()
        );
        assertEquals(70, skill.getDamagePercent());
        assertEquals(20, skill.getCooldown());
        assertEquals(SkillContext.SkillTier.MAJOR, first.getTier());
        assertEquals(0.70F, first.getDamageMultiplier());
        assertEquals(0.70F, second.getDamageMultiplier());
        assertEquals(0.90F, markedSecond.getDamageMultiplier());
        assertTrue(first.isGuaranteedCrit());
        assertTrue(second.isGuaranteedCrit());
        assertTrue(markedSecond.isGuaranteedCrit());
        assertFalse(first.isIgnoreDefense());
        assertEquals(
                12.0F,
                InfinityDaggerSingularityBackstabSkillHandler.ENERGY_COST
        );
        assertEquals(
                5.0D,
                InfinityDaggerSingularityBackstabSkillHandler.TARGET_RANGE
        );
        assertEquals(
                3.0D,
                InfinityDaggerSingularityBackstabSkillHandler
                        .BEHIND_DISTANCE
        );
        assertEquals(
                0.20F,
                InfinityDaggerSingularityBackstabSkillHandler
                        .MARKED_SECOND_HIT_BONUS
        );
        assertEquals(
                24,
                InfinityDaggerSingularityBackstabSkillHandler
                        .FREEZE_DURATION_TICKS
        );
        assertEquals(
                5,
                InfinityDaggerSingularityBackstabSkillHandler
                        .HIT_CONTEXT_LIFETIME_TICKS
        );
        assertEquals(
                6,
                InfinityDaggerSingularityBackstabSkillHandler
                        .SECOND_HIT_ANIMATION_TICKS
        );
        assertEquals(
                8,
                InfinityDaggerSingularityBackstabSkillHandler
                        .FINAL_ANIMATION_TICKS
        );
        assertTrue(
                new InfinityDaggerSingularityBackstabSkillHandler()
                        .completesImmediately()
        );
    }

    @Test
    void secondHitOnlyOccursWhileTheTargetSurvives() {
        assertTrue(
                InfinityDaggerSingularityBackstabSkillHandler
                        .shouldStrikeSecond(true)
        );
        assertFalse(
                InfinityDaggerSingularityBackstabSkillHandler
                        .shouldStrikeSecond(false)
        );
    }

    @Test
    void castContextRejectsDeathRemovalAndDimensionChanges() {
        assertTrue(
                InfinityDaggerSingularityBackstabSkillHandler
                        .isCastContextValid(true, true, true)
        );
        assertFalse(
                InfinityDaggerSingularityBackstabSkillHandler
                        .isCastContextValid(false, true, true)
        );
        assertFalse(
                InfinityDaggerSingularityBackstabSkillHandler
                        .isCastContextValid(true, false, true)
        );
        assertFalse(
                InfinityDaggerSingularityBackstabSkillHandler
                        .isCastContextValid(true, true, false)
        );
        assertTrue(
                InfinityDaggerSingularityBackstabSkillHandler
                        .canContinueCast(true, true)
        );
        assertFalse(
                InfinityDaggerSingularityBackstabSkillHandler
                        .canContinueCast(false, true)
        );
        assertFalse(
                InfinityDaggerSingularityBackstabSkillHandler
                        .canContinueCast(true, false)
        );
    }

    @Test
    void energyGateKeepsCreativeAndBlessingExemptions() {
        assertFalse(
                InfinityDaggerSingularityBackstabSkillHandler
                        .canPayEnergy(11.99F, false, false)
        );
        assertTrue(
                InfinityDaggerSingularityBackstabSkillHandler
                        .canPayEnergy(12.0F, false, false)
        );
        assertTrue(
                InfinityDaggerSingularityBackstabSkillHandler
                        .canPayEnergy(0.0F, true, false)
        );
        assertTrue(
                InfinityDaggerSingularityBackstabSkillHandler
                        .canPayEnergy(0.0F, false, true)
        );
    }

    @Test
    void safeSearchKeepsBehindPositionFirstAndRotatedFallbacks() {
        List<Vec3> candidates =
                InfinityDaggerSingularityBackstabSkillHandler
                        .behindCandidates(
                                new Vec3(10.0D, 64.0D, 10.0D),
                                new Vec3(0.0D, 0.0D, 1.0D),
                                new Vec3(10.0D, 64.0D, 5.0D),
                                0.6D,
                                3.0D
                        );

        assertEquals(9, candidates.size());
        assertEquals(10.0D, candidates.getFirst().x, 1.0E-9D);
        assertEquals(64.0D, candidates.getFirst().y, 1.0E-9D);
        assertEquals(6.7D, candidates.getFirst().z, 1.0E-9D);
        assertTrue(candidates.get(1).x > candidates.getFirst().x);
        assertTrue(candidates.get(2).x < candidates.getFirst().x);
    }
}
