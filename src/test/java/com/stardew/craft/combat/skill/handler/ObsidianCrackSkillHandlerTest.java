package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.ObsidianCrackTracker;
import com.stardew.craft.item.weapon.WeaponData;
import com.stardew.craft.item.weapon.WeaponRegistry;
import com.stardew.craft.item.weapon.WeaponSkillData;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObsidianCrackSkillHandlerTest {
    @Test
    void authoredSkillAndDelayedCrackContractRemainStable() {
        WeaponData obsidianEdge = WeaponRegistry.get("obsidian_edge");
        assertNotNull(obsidianEdge);
        WeaponSkillData skill = obsidianEdge.getSkill2();
        assertNotNull(skill);

        assertEquals("obsidian_crack", skill.getId());
        assertEquals(160, skill.getDamagePercent());
        assertEquals(10, skill.getCooldown());
        assertEquals(10.0F, ObsidianCrackSkillHandler.ENERGY_COST);
        assertEquals(6.0F, ObsidianCrackSkillHandler.LINE_LENGTH);
        assertEquals(3.0D, ObsidianCrackSkillHandler.FORWARD_OFFSET);
        assertEquals(12, ObsidianCrackSkillHandler.ANIMATION_TICKS);
        assertEquals(8, ObsidianCrackTracker.EXPLODE_DELAY_TICKS);
        assertFalse(new ObsidianCrackSkillHandler().completesImmediately());
    }

    @Test
    void lineIsSixBlocksWideAndThreeBlocksAheadOfTheCaster() {
        ObsidianCrackSkillHandler.CrackLine line =
                ObsidianCrackSkillHandler.createCrackLine(
                        new Vec3(10.0D, 64.0D, 10.0D),
                        new Vec3(0.0D, 0.0D, 1.0D)
                );

        assertEquals(7.0D, line.start().x);
        assertEquals(64.0D, line.start().y);
        assertEquals(13.0D, line.start().z);
        assertEquals(13.0D, line.end().x);
        assertEquals(64.0D, line.end().y);
        assertEquals(13.0D, line.end().z);
        assertEquals(-90.0F, line.yaw());
        assertEquals(6.0F, line.length());
    }

    @Test
    void energyPreflightPreservesCreativeAndBlessingFreeCasts() {
        assertFalse(ObsidianCrackSkillHandler.canPayEnergy(
                9.99F,
                false,
                false
        ));
        assertTrue(ObsidianCrackSkillHandler.canPayEnergy(
                10.0F,
                false,
                false
        ));
        assertTrue(ObsidianCrackSkillHandler.canPayEnergy(
                0.0F,
                true,
                false
        ));
        assertTrue(ObsidianCrackSkillHandler.canPayEnergy(
                0.0F,
                false,
                true
        ));
    }
}
