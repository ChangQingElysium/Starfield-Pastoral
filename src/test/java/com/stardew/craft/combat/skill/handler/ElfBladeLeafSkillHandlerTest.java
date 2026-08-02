package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.item.weapon.WeaponData;
import com.stardew.craft.item.weapon.WeaponRegistry;
import com.stardew.craft.item.weapon.WeaponSkillData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElfBladeLeafSkillHandlerTest {
    @Test
    void preservesTheAuthoredLeafStateContract() {
        WeaponData elfBlade = WeaponRegistry.get("elf_blade");
        assertNotNull(elfBlade);
        WeaponSkillData skill = elfBlade.getSkill1();
        assertNotNull(skill);

        assertEquals("elf_blade_leaf", skill.getId());
        assertEquals(50, skill.getDamagePercent());
        assertEquals(7, skill.getCooldown());
        assertEquals(3, ElfBladeLeafSkillHandler.LEAF_COUNT);
        assertEquals(100, ElfBladeLeafSkillHandler.ACTIVE_DURATION_TICKS);
        assertEquals(8, ElfBladeLeafSkillHandler.ANIMATION_TICKS);
        assertFalse(new ElfBladeLeafSkillHandler().completesImmediately());
    }

    @Test
    void onlyNonNaturalTerminationDiscardsOwnedLeaves() {
        assertFalse(ElfBladeLeafSkillHandler.shouldDiscardLeaves(
                SkillInstance.EndReason.COMPLETED
        ));
        assertTrue(ElfBladeLeafSkillHandler.shouldDiscardLeaves(
                SkillInstance.EndReason.INTERRUPTED
        ));
        assertTrue(ElfBladeLeafSkillHandler.shouldDiscardLeaves(
                SkillInstance.EndReason.INVALIDATED
        ));
        assertTrue(ElfBladeLeafSkillHandler.shouldDiscardLeaves(
                SkillInstance.EndReason.CASTER_UNAVAILABLE
        ));
    }
}
