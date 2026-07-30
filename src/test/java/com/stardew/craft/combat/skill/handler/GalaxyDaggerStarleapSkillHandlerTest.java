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

class GalaxyDaggerStarleapSkillHandlerTest {
    @Test
    void preservesTheAuthoredStarLeapContract() {
        WeaponData galaxyDagger = WeaponRegistry.get("galaxy_dagger");
        assertNotNull(galaxyDagger);
        WeaponSkillData skill = galaxyDagger.getSkill2();
        assertNotNull(skill);

        SkillContext normal =
                GalaxyDaggerStarleapSkillHandler.createHitContext(
                        skill,
                        false
                );
        SkillContext marked =
                GalaxyDaggerStarleapSkillHandler.createHitContext(
                        skill,
                        true
                );

        assertEquals("galaxy_dagger_starleap", skill.getId());
        assertEquals(140, skill.getDamagePercent());
        assertEquals(18, skill.getCooldown());
        assertEquals(SkillContext.SkillTier.MAJOR, normal.getTier());
        assertEquals(1.40F, normal.getDamageMultiplier());
        assertEquals(1.70F, marked.getDamageMultiplier());
        assertTrue(normal.isGuaranteedCrit());
        assertTrue(marked.isGuaranteedCrit());
        assertFalse(normal.isIgnoreDefense());
        assertEquals(
                10.0F,
                GalaxyDaggerStarleapSkillHandler.ENERGY_COST
        );
        assertEquals(
                5.0D,
                GalaxyDaggerStarleapSkillHandler.TARGET_RANGE
        );
        assertEquals(
                3.0D,
                GalaxyDaggerStarleapSkillHandler.BEHIND_DISTANCE
        );
        assertEquals(
                0.30F,
                GalaxyDaggerStarleapSkillHandler.MARK_DAMAGE_BONUS
        );
        assertEquals(
                16,
                GalaxyDaggerStarleapSkillHandler.FREEZE_DURATION_TICKS
        );
        assertEquals(
                5,
                GalaxyDaggerStarleapSkillHandler
                        .HIT_CONTEXT_LIFETIME_TICKS
        );
        assertEquals(
                8,
                GalaxyDaggerStarleapSkillHandler.ANIMATION_TICKS
        );
        assertTrue(
                new GalaxyDaggerStarleapSkillHandler()
                        .completesImmediately()
        );
    }

    @Test
    void energyGateKeepsCreativeAndBlessingExemptions() {
        assertFalse(
                GalaxyDaggerStarleapSkillHandler.canPayEnergy(
                        9.99F,
                        false,
                        false
                )
        );
        assertTrue(
                GalaxyDaggerStarleapSkillHandler.canPayEnergy(
                        10.0F,
                        false,
                        false
                )
        );
        assertTrue(
                GalaxyDaggerStarleapSkillHandler.canPayEnergy(
                        0.0F,
                        true,
                        false
                )
        );
        assertTrue(
                GalaxyDaggerStarleapSkillHandler.canPayEnergy(
                        0.0F,
                        false,
                        true
                )
        );
    }

    @Test
    void safeSearchKeepsBehindPositionFirstAndRotatedFallbacks() {
        List<Vec3> candidates =
                GalaxyDaggerStarleapSkillHandler.behindCandidates(
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
