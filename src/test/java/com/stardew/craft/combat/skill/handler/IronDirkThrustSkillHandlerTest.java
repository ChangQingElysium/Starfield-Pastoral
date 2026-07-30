package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.item.weapon.WeaponData;
import com.stardew.craft.item.weapon.WeaponRegistry;
import com.stardew.craft.item.weapon.WeaponSkillData;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IronDirkThrustSkillHandlerTest {
    @Test
    void hitContextPreservesTheAuthoredSingleStrikeContract() {
        WeaponData ironDirk = WeaponRegistry.get("iron_dirk");
        assertNotNull(ironDirk);
        WeaponSkillData skill = ironDirk.getSkill1();
        assertNotNull(skill);

        SkillContext context = IronDirkThrustSkillHandler.createHitContext(skill);

        assertEquals("iron_dirk_thrust", context.getSkillId());
        assertEquals(SkillContext.SkillTier.MINOR, context.getTier());
        assertEquals(1.5F, context.getDamageMultiplier());
        assertEquals(0.10F, context.getCritChanceBonus());
        assertFalse(context.isGuaranteedCrit());
        assertFalse(context.isIgnoreDefense());
        assertEquals(6, skill.getCooldown());
    }

    @Test
    void movementAndProtectionConstantsRemainUnchanged() {
        assertEquals(7.0, IronDirkThrustSkillHandler.TARGET_RANGE);
        assertEquals(3.0, IronDirkThrustSkillHandler.BEHIND_DISTANCE);
        assertEquals(5, IronDirkThrustSkillHandler.RESISTANCE_DURATION_TICKS);
        assertEquals(0, IronDirkThrustSkillHandler.RESISTANCE_AMPLIFIER);
        assertEquals(5, IronDirkThrustSkillHandler.HIT_CONTEXT_LIFETIME_TICKS);
        assertEquals(8, IronDirkThrustSkillHandler.ANIMATION_TICKS);
        assertTrue(new IronDirkThrustSkillHandler().completesImmediately());
    }

    @Test
    void safePositionSearchRotationPreservesVectorLengthAndVerticalComponent() {
        Vec3 original = new Vec3(3.0, 0.25, 4.0);
        Vec3 rotated = IronDirkThrustSkillHandler.rotateVector(
                original,
                Math.PI / 2.0
        );

        assertEquals(original.lengthSqr(), rotated.lengthSqr(), 1.0E-9);
        assertEquals(original.y, rotated.y, 1.0E-9);
        assertEquals(-4.0, rotated.x, 1.0E-9);
        assertEquals(3.0, rotated.z, 1.0E-9);
    }
}
