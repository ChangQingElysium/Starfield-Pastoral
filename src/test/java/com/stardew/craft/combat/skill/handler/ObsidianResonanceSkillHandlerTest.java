package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.ObsidianResonanceTracker;
import com.stardew.craft.item.weapon.WeaponData;
import com.stardew.craft.item.weapon.WeaponRegistry;
import com.stardew.craft.item.weapon.WeaponSkillData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObsidianResonanceSkillHandlerTest {
    @Test
    void passiveSlotAndChargeContractRemainStable() {
        WeaponData obsidianEdge = WeaponRegistry.get("obsidian_edge");
        assertNotNull(obsidianEdge);
        WeaponSkillData skill = obsidianEdge.getSkill1();
        assertNotNull(skill);

        assertEquals("obsidian_resonance", skill.getId());
        assertEquals(70, skill.getDamagePercent());
        assertEquals(7, skill.getCooldown());
        assertEquals(140, ObsidianResonanceTracker.CHARGE_TICKS);
        assertTrue(new ObsidianResonanceSkillHandler().completesImmediately());
    }
}
