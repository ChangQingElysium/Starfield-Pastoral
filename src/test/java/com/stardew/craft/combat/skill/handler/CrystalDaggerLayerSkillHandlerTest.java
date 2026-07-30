package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.CrystalDaggerLayerTracker;
import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.item.weapon.WeaponData;
import com.stardew.craft.item.weapon.WeaponRegistry;
import com.stardew.craft.item.weapon.WeaponSkillData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrystalDaggerLayerSkillHandlerTest {
    @Test
    void preservesTheAuthoredHitLayerAndNoTargetDashContract() {
        WeaponData crystalDagger = WeaponRegistry.get("crystal_dagger");
        assertNotNull(crystalDagger);
        WeaponSkillData skill = crystalDagger.getSkill1();
        assertNotNull(skill);

        SkillContext hitContext =
                CrystalDaggerLayerSkillHandler.createHitContext(skill);

        assertEquals("crystal_dagger_layer", hitContext.getSkillId());
        assertEquals(SkillContext.SkillTier.MINOR, hitContext.getTier());
        assertEquals(1.2F, hitContext.getDamageMultiplier());
        assertFalse(hitContext.isIgnoreDefense());
        assertFalse(hitContext.isGuaranteedCrit());
        assertEquals(0.0F, hitContext.getCritChanceBonus());
        assertEquals(6, skill.getCooldown());
        assertEquals(4.0, CrystalDaggerLayerSkillHandler.TARGET_RANGE);
        assertEquals(
                3.0,
                CrystalDaggerLayerSkillHandler.NO_TARGET_DASH_DISTANCE
        );
        assertEquals(5, CrystalDaggerLayerSkillHandler.DASH_DURATION_TICKS);
        assertEquals(
                5,
                CrystalDaggerLayerSkillHandler.HIT_CONTEXT_LIFETIME_TICKS
        );
        assertEquals(8, CrystalDaggerLayerSkillHandler.ANIMATION_TICKS);
        assertEquals(4, CrystalDaggerLayerTracker.MAX_STACKS);
        assertEquals(120, CrystalDaggerLayerTracker.DURATION_TICKS);
        assertTrue(new CrystalDaggerLayerSkillHandler().completesImmediately());
    }
}
