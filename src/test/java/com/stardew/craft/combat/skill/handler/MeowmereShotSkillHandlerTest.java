package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.entity.projectile.MeowmereProjectileEntity;
import com.stardew.craft.item.weapon.WeaponData;
import com.stardew.craft.item.weapon.WeaponRegistry;
import com.stardew.craft.item.weapon.WeaponSkillData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeowmereShotSkillHandlerTest {
    @Test
    void preservesTheAuthoredSingleRainbowBoltContract() {
        WeaponData meowmere = WeaponRegistry.get("meowmere");
        assertNotNull(meowmere);
        WeaponSkillData skill = meowmere.getSkill1();
        assertNotNull(skill);

        assertEquals("meowmere_shot", skill.getId());
        assertEquals(100, skill.getDamagePercent());
        assertEquals(4, skill.getCooldown());
        assertEquals(
                20.0F,
                MeowmereShotSkillHandler.projectileDamage(meowmere)
        );
        assertEquals(0, MeowmereShotSkillHandler.PIERCE_COUNT);
        assertEquals(1.1F, MeowmereShotSkillHandler.PROJECTILE_SPEED);
        assertEquals(
                1.0F,
                MeowmereShotSkillHandler.PROJECTILE_INACCURACY
        );
        assertEquals(
                MeowmereProjectileEntity.MAX_LIFETIME_TICKS + 1,
                MeowmereShotSkillHandler.PROJECTILE_RUNTIME_TICKS
        );
        assertEquals(10, MeowmereShotSkillHandler.ANIMATION_TICKS);
        assertFalse(new MeowmereShotSkillHandler().completesImmediately());
    }

    @Test
    void runtimeTimeoutUsesTheAuthoredInclusiveEndBoundary() {
        long endTick = 301L;

        assertFalse(MeowmereShotSkillHandler.hasTimedOut(
                endTick - 1L,
                endTick
        ));
        assertTrue(MeowmereShotSkillHandler.hasTimedOut(endTick, endTick));
    }
}
