package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.item.weapon.WeaponData;
import com.stardew.craft.item.weapon.WeaponRegistry;
import com.stardew.craft.item.weapon.WeaponSkillData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class InfinityDaggerSingularityStabSkillHandlerTest {
    @Test
    void preservesTheAuthoredTargetCooldownAndActivationContract() {
        WeaponData infinityDagger = WeaponRegistry.get("infinity_dagger");
        assertNotNull(infinityDagger);
        WeaponSkillData skill = infinityDagger.getSkill1();
        assertNotNull(skill);

        assertEquals(
                "infinity_dagger_singularity_stab",
                skill.getId()
        );
        assertEquals(22, skill.getDamagePercent());
        assertEquals(7, skill.getCooldown());
        assertEquals(
                3.5D,
                InfinityDaggerSingularityStabSkillHandler
                        .INITIAL_TARGET_RANGE
        );
        assertEquals(
                6,
                InfinityDaggerSingularityStabSkillHandler
                        .INITIAL_ANIMATION_TICKS
        );
        assertFalse(
                new InfinityDaggerSingularityStabSkillHandler()
                        .completesImmediately()
        );
    }
}
