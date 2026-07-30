package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.LavaKatanaMarkTracker;
import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.item.weapon.WeaponData;
import com.stardew.craft.item.weapon.WeaponRegistry;
import com.stardew.craft.item.weapon.WeaponSkillData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LavaKatanaBrandSkillHandlerTest {
    @Test
    void authoredBrandHitAndMarkContractRemainStable() {
        WeaponData lavaKatana = WeaponRegistry.get("lava_katana");
        assertNotNull(lavaKatana);
        WeaponSkillData skill = lavaKatana.getSkill1();
        assertNotNull(skill);

        SkillContext hit =
                LavaKatanaBrandSkillHandler.createHitContext(skill);

        assertEquals("lava_katana_brand", hit.getSkillId());
        assertEquals(SkillContext.SkillTier.MINOR, hit.getTier());
        assertEquals(1.10F, hit.getDamageMultiplier());
        assertFalse(hit.isGuaranteedCrit());
        assertFalse(hit.isIgnoreDefense());
        assertEquals(12, skill.getCooldown());
        assertEquals(5.5D, LavaKatanaBrandSkillHandler.TARGET_RANGE);
        assertEquals(
                5,
                LavaKatanaBrandSkillHandler.HIT_CONTEXT_LIFETIME_TICKS
        );
        assertEquals(8, LavaKatanaBrandSkillHandler.ANIMATION_TICKS);
        assertEquals(120, LavaKatanaMarkTracker.MARK_DURATION_TICKS);
        assertTrue(
                new LavaKatanaBrandSkillHandler().completesImmediately()
        );
    }
}
