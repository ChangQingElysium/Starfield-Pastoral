package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.SingularityTracker;
import com.stardew.craft.item.weapon.WeaponData;
import com.stardew.craft.item.weapon.WeaponRegistry;
import com.stardew.craft.item.weapon.WeaponSkillData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SingularityEvolveSkillHandlerTest {
    @Test
    void authoredActivationAndRewardContractRemainStable() {
        WeaponData infinityBlade = WeaponRegistry.get("infinity_blade");
        assertNotNull(infinityBlade);
        WeaponSkillData skill = infinityBlade.getSkill1();
        assertNotNull(skill);

        assertEquals("singularity_evolve", skill.getId());
        assertEquals(160, skill.getDamagePercent());
        assertEquals(6, skill.getCooldown());
        assertEquals(
                4,
                SingularityEvolveSkillHandler
                        .HIT_SINGULARITY_RESTORE
        );
        assertEquals(
                10.0F,
                SingularityEvolveSkillHandler.HIT_ENERGY_RESTORE
        );
        assertEquals(
                5.0F,
                SingularityEvolveSkillHandler.HIT_HEALTH_RESTORE
        );
        assertEquals(8, SingularityEvolveSkillHandler.ANIMATION_TICKS);
        assertFalse(
                new SingularityEvolveSkillHandler()
                        .completesImmediately()
        );
    }

    @Test
    void evolvedStateIsSnapshottedBeforeThisCastsStackReward() {
        assertFalse(
                SingularityEvolveSkillHandler.evolvedForStacks(
                        SingularityTracker.EVOLVE_THRESHOLD - 1
                )
        );
        assertTrue(
                SingularityEvolveSkillHandler.evolvedForStacks(
                        SingularityTracker.EVOLVE_THRESHOLD
                )
        );
        assertEquals(
                20,
                SingularityEvolveSkillHandler.ACTIVE_DURATION_TICKS
        );
    }
}
