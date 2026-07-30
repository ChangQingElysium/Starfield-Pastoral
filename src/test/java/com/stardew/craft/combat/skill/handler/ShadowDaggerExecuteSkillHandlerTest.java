package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.item.weapon.WeaponData;
import com.stardew.craft.item.weapon.WeaponRegistry;
import com.stardew.craft.item.weapon.WeaponSkillData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShadowDaggerExecuteSkillHandlerTest {
    @Test
    void preservesTheAuthoredNormalAndExecuteHitContracts() {
        WeaponData shadowDagger = WeaponRegistry.get("shadow_dagger");
        assertNotNull(shadowDagger);
        WeaponSkillData skill = shadowDagger.getSkill1();
        assertNotNull(skill);

        SkillContext normal = ShadowDaggerExecuteSkillHandler.createHitContext(skill);
        SkillContext execute = ShadowDaggerExecuteSkillHandler.createExecuteBonusContext();

        assertEquals("shadow_dagger_execute", normal.getSkillId());
        assertEquals(SkillContext.SkillTier.MINOR, normal.getTier());
        assertEquals(1.2F, normal.getDamageMultiplier());
        assertFalse(normal.isGuaranteedCrit());
        assertFalse(normal.isIgnoreDefense());

        assertEquals("shadow_dagger_execute_bonus", execute.getSkillId());
        assertEquals(SkillContext.SkillTier.MINOR, execute.getTier());
        assertEquals(1.0F, execute.getDamageMultiplier());
        assertFalse(execute.isGuaranteedCrit());
        assertFalse(execute.isIgnoreDefense());
    }

    @Test
    void targetingCooldownAndLifecycleConstantsRemainUnchanged() {
        WeaponData shadowDagger = WeaponRegistry.get("shadow_dagger");
        assertNotNull(shadowDagger);
        assertEquals(6, shadowDagger.getSkill1().getCooldown());
        assertEquals(4.0, ShadowDaggerExecuteSkillHandler.TARGET_RANGE);
        assertEquals(0.30F, ShadowDaggerExecuteSkillHandler.EXECUTE_HEALTH_RATIO);
        assertEquals(5, ShadowDaggerExecuteSkillHandler.HIT_CONTEXT_LIFETIME_TICKS);
        assertEquals(8, ShadowDaggerExecuteSkillHandler.ANIMATION_TICKS);
        assertTrue(new ShadowDaggerExecuteSkillHandler().completesImmediately());
    }
}
