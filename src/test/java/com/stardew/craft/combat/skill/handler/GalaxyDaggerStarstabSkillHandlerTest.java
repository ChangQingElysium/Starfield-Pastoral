package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.item.weapon.WeaponData;
import com.stardew.craft.item.weapon.WeaponRegistry;
import com.stardew.craft.item.weapon.WeaponSkillData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GalaxyDaggerStarstabSkillHandlerTest {
    @Test
    void preservesTheAuthoredTargetCooldownAndActivationContract() {
        WeaponData galaxyDagger = WeaponRegistry.get("galaxy_dagger");
        assertNotNull(galaxyDagger);
        WeaponSkillData skill = galaxyDagger.getSkill1();
        assertNotNull(skill);

        assertEquals("galaxy_dagger_starstab", skill.getId());
        assertEquals(50, skill.getDamagePercent());
        assertEquals(7, skill.getCooldown());
        assertEquals(
                3.5D,
                GalaxyDaggerStarstabSkillHandler.INITIAL_TARGET_RANGE
        );
        assertEquals(
                6,
                GalaxyDaggerStarstabSkillHandler.INITIAL_ANIMATION_TICKS
        );
        assertFalse(
                new GalaxyDaggerStarstabSkillHandler()
                        .completesImmediately()
        );
    }
}
