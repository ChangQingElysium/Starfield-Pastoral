package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.item.weapon.WeaponData;
import com.stardew.craft.item.weapon.WeaponRegistry;
import com.stardew.craft.item.weapon.WeaponSkillData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YetiToothSpineSkillHandlerTest {
    @Test
    void preservesTheAuthoredIceSpineContract() {
        WeaponData yetiTooth = WeaponRegistry.get("yeti_tooth");
        assertNotNull(yetiTooth);
        WeaponSkillData skill = yetiTooth.getSkill2();
        assertNotNull(skill);

        assertEquals("yeti_tooth_spine", skill.getId());
        assertEquals(180, skill.getDamagePercent());
        assertEquals(20, skill.getCooldown());
        assertEquals(10.0F, YetiToothSpineSkillHandler.ENERGY_COST);
        assertEquals(8, YetiToothSpineSkillHandler.ANIMATION_TICKS);
        assertEquals(5, YetiToothSpineSkillHandler.SPINE_COUNT);
        assertEquals(120.0F, YetiToothSpineSkillHandler.ARC_DEGREES);
        assertEquals(
                30.0F,
                YetiToothSpineSkillHandler.ANGLE_STEP_DEGREES
        );
        assertEquals(2.5D, YetiToothSpineSkillHandler.SPAWN_RADIUS);
        assertFalse(new YetiToothSpineSkillHandler().completesImmediately());
    }

    @Test
    void energyValidationHonorsCreativeAndTheFreeEnergyBlessing() {
        assertFalse(YetiToothSpineSkillHandler.canPayEnergy(
                9.99F,
                false,
                false
        ));
        assertTrue(YetiToothSpineSkillHandler.canPayEnergy(
                10.0F,
                false,
                false
        ));
        assertTrue(YetiToothSpineSkillHandler.canPayEnergy(
                0.0F,
                true,
                false
        ));
        assertTrue(YetiToothSpineSkillHandler.canPayEnergy(
                0.0F,
                false,
                true
        ));
    }
}
