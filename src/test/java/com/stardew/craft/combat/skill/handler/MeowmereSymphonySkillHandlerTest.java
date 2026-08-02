package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.runtime.SkillTickResult;
import com.stardew.craft.entity.projectile.MeowmereProjectileEntity;
import com.stardew.craft.item.weapon.WeaponData;
import com.stardew.craft.item.weapon.WeaponRegistry;
import com.stardew.craft.item.weapon.WeaponSkillData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeowmereSymphonySkillHandlerTest {
    @Test
    void preservesTheAuthoredFiveShotFanContract() {
        WeaponData meowmere = WeaponRegistry.get("meowmere");
        assertNotNull(meowmere);
        WeaponSkillData skill = meowmere.getSkill2();
        assertNotNull(skill);

        assertEquals("meowmere_symphony", skill.getId());
        assertEquals(80, skill.getDamagePercent());
        assertEquals(15, skill.getCooldown());
        assertEquals(10.0F, MeowmereSymphonySkillHandler.ENERGY_COST);
        assertEquals(5, MeowmereSymphonySkillHandler.PROJECTILE_COUNT);
        assertEquals(1, MeowmereSymphonySkillHandler.PIERCE_COUNT);
        assertEquals(
                16.0F,
                MeowmereSymphonySkillHandler.projectileDamage(
                        meowmere,
                        skill.getDamagePercent()
                )
        );
        assertEquals(
                1.0F,
                MeowmereSymphonySkillHandler.PROJECTILE_SPEED
        );
        assertEquals(
                1.0F,
                MeowmereSymphonySkillHandler.PROJECTILE_INACCURACY
        );
        assertEquals(
                MeowmereProjectileEntity.MAX_LIFETIME_TICKS + 1,
                MeowmereSymphonySkillHandler.PROJECTILE_RUNTIME_TICKS
        );
        assertEquals(15, MeowmereSymphonySkillHandler.ANIMATION_TICKS);
        assertFalse(
                new MeowmereSymphonySkillHandler().completesImmediately()
        );
    }

    @Test
    void fanKeepsTheOriginalEightDegreeYawSpacing() {
        assertEquals(
                -16.0F,
                MeowmereSymphonySkillHandler.yawOffsetDegrees(0)
        );
        assertEquals(
                -8.0F,
                MeowmereSymphonySkillHandler.yawOffsetDegrees(1)
        );
        assertEquals(
                0.0F,
                MeowmereSymphonySkillHandler.yawOffsetDegrees(2)
        );
        assertEquals(
                8.0F,
                MeowmereSymphonySkillHandler.yawOffsetDegrees(3)
        );
        assertEquals(
                16.0F,
                MeowmereSymphonySkillHandler.yawOffsetDegrees(4)
        );
    }

    @Test
    void energyValidationHonorsCreativeAndTheFreeEnergyBlessing() {
        assertFalse(MeowmereSymphonySkillHandler.canPayEnergy(
                9.99F,
                false,
                false
        ));
        assertTrue(MeowmereSymphonySkillHandler.canPayEnergy(
                10.0F,
                false,
                false
        ));
        assertTrue(MeowmereSymphonySkillHandler.canPayEnergy(
                0.0F,
                true,
                false
        ));
        assertTrue(MeowmereSymphonySkillHandler.canPayEnergy(
                0.0F,
                false,
                true
        ));
    }

    @Test
    void volleyContinuesUntilEveryProjectileIsGoneOrItTimesOut() {
        assertEquals(
                SkillTickResult.CONTINUE,
                MeowmereSymphonySkillHandler.status(
                        true,
                        false,
                        300L,
                        301L
                )
        );
        assertEquals(
                SkillTickResult.COMPLETE,
                MeowmereSymphonySkillHandler.status(
                        true,
                        true,
                        250L,
                        301L
                )
        );
        assertEquals(
                SkillTickResult.COMPLETE,
                MeowmereSymphonySkillHandler.status(
                        true,
                        false,
                        301L,
                        301L
                )
        );
    }

    @Test
    void projectileLeavingTheCastDimensionCancelsTheVolley() {
        assertEquals(
                SkillTickResult.CANCEL,
                MeowmereSymphonySkillHandler.status(
                        false,
                        false,
                        250L,
                        301L
                )
        );
    }
}
