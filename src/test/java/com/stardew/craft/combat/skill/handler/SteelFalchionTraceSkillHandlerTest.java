package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.SteelFalchionLineTracker;
import com.stardew.craft.item.weapon.WeaponData;
import com.stardew.craft.item.weapon.WeaponRegistry;
import com.stardew.craft.item.weapon.WeaponSkillData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SteelFalchionTraceSkillHandlerTest {
    @Test
    void authoredTraceDataAndRuntimeContractRemainStable() {
        WeaponData steelFalchion = WeaponRegistry.get("steel_falchion");
        assertNotNull(steelFalchion);
        WeaponSkillData skill = steelFalchion.getSkill2();
        assertNotNull(skill);

        assertEquals("steel_falchion_trace", skill.getId());
        assertEquals(50, skill.getDamagePercent());
        assertEquals(20, skill.getCooldown());
        assertEquals(12.0F, SteelFalchionTraceSkillHandler.ENERGY_COST);
        assertEquals(
                100,
                SteelFalchionTraceSkillHandler.TRACE_DURATION_TICKS
        );
        assertEquals(
                0.50F,
                SteelFalchionTraceSkillHandler
                        .TRACE_DOT_DAMAGE_MULTIPLIER
        );
        assertEquals(2, SteelFalchionLineTracker.TRACE_SPEED_AMPLIFIER);
        assertFalse(
                new SteelFalchionTraceSkillHandler().completesImmediately()
        );
    }

    @Test
    void energyPreflightPreservesCreativeAndBlessingFreeCasts() {
        assertFalse(SteelFalchionTraceSkillHandler.canPayEnergy(
                11.99F,
                false,
                false
        ));
        assertTrue(SteelFalchionTraceSkillHandler.canPayEnergy(
                12.0F,
                false,
                false
        ));
        assertTrue(SteelFalchionTraceSkillHandler.canPayEnergy(
                0.0F,
                true,
                false
        ));
        assertTrue(SteelFalchionTraceSkillHandler.canPayEnergy(
                0.0F,
                false,
                true
        ));
    }
}
