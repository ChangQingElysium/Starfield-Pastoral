package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.entity.projectile.TideAnchorProjectileEntity;
import com.stardew.craft.item.weapon.WeaponData;
import com.stardew.craft.item.weapon.WeaponRegistry;
import com.stardew.craft.item.weapon.WeaponSkillData;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TideAnchorSkillHandlerTest {
    @Test
    void preservesTheAuthoredProjectileCastContract() {
        WeaponData neptunesGlaive = WeaponRegistry.get("neptunes_glaive");
        assertNotNull(neptunesGlaive);
        WeaponSkillData skill = neptunesGlaive.getSkill2();
        assertNotNull(skill);

        assertEquals("tide_anchor", skill.getId());
        assertEquals(150, skill.getDamagePercent());
        assertEquals(20, skill.getCooldown());
        assertEquals(10.0F, TideAnchorSkillHandler.ENERGY_COST);
        assertEquals(1.25F, TideAnchorSkillHandler.PROJECTILE_SPEED);
        assertEquals(0.8F, TideAnchorSkillHandler.PROJECTILE_INACCURACY);
        assertEquals(81, TideAnchorSkillHandler.PROJECTILE_RUNTIME_TICKS);
        assertEquals(12, TideAnchorSkillHandler.ANIMATION_TICKS);
        assertEquals(4.5, TideAnchorProjectileEntity.AOE_RADIUS);
        assertEquals(24.0, TideAnchorProjectileEntity.MARK_TELEPORT_RADIUS);
        assertFalse(new TideAnchorSkillHandler().completesImmediately());
    }

    @Test
    void energyValidationHonorsCreativeAndTheFreeEnergyBlessing() {
        assertFalse(TideAnchorSkillHandler.canPayEnergy(
                9.99F,
                false,
                false
        ));
        assertTrue(TideAnchorSkillHandler.canPayEnergy(
                10.0F,
                false,
                false
        ));
        assertTrue(TideAnchorSkillHandler.canPayEnergy(
                0.0F,
                true,
                false
        ));
        assertTrue(TideAnchorSkillHandler.canPayEnergy(
                0.0F,
                false,
                true
        ));
    }

    @Test
    void projectileLifecycleIsBoundToTheCastDimension() {
        assertTrue(TideAnchorSkillHandler.isSameDimension(
                Level.OVERWORLD,
                Level.OVERWORLD
        ));
        assertFalse(TideAnchorSkillHandler.isSameDimension(
                Level.OVERWORLD,
                Level.NETHER
        ));
    }
}
