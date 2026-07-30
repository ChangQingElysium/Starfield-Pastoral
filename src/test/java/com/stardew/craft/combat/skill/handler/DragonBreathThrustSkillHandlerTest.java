package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.combat.skill.runtime.SkillInstance;
import com.stardew.craft.item.weapon.WeaponData;
import com.stardew.craft.item.weapon.WeaponRegistry;
import com.stardew.craft.item.weapon.WeaponSkillData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DragonBreathThrustSkillHandlerTest {
    @Test
    void preservesTheAuthoredPiercingHitAndPresentationContract() {
        WeaponData cutlass = WeaponRegistry.get("dragontooth_cutlass");
        assertNotNull(cutlass);
        WeaponSkillData skill = cutlass.getSkill1();
        assertNotNull(skill);

        SkillContext context =
                DragonBreathThrustSkillHandler.createHitContext(skill);

        assertEquals("dragon_breath_thrust", skill.getId());
        assertEquals(120, skill.getDamagePercent());
        assertEquals(8, skill.getCooldown());
        assertEquals(SkillContext.SkillTier.MINOR, context.getTier());
        assertEquals(1.2F, context.getDamageMultiplier());
        assertEquals(0.10F, context.getCritChanceBonus());
        assertFalse(context.isGuaranteedCrit());
        assertFalse(context.isIgnoreDefense());

        assertEquals(5.0D, DragonBreathThrustSkillHandler.DASH_DISTANCE);
        assertEquals(0.9D, DragonBreathThrustSkillHandler.PATH_HIT_RADIUS);
        assertEquals(5, DragonBreathThrustSkillHandler.DASH_DURATION_TICKS);
        assertEquals(
                80,
                DragonBreathThrustSkillHandler.VULNERABLE_DURATION_TICKS
        );
        assertEquals(
                1,
                DragonBreathThrustSkillHandler.VULNERABLE_AMPLIFIER
        );
        assertEquals(
                40,
                DragonBreathThrustSkillHandler.STAGGER_DURATION_TICKS
        );
        assertEquals(
                5,
                DragonBreathThrustSkillHandler
                        .HIT_CONTEXT_LIFETIME_TICKS
        );
        assertEquals(8, DragonBreathThrustSkillHandler.ANIMATION_TICKS);
        assertFalse(
                new DragonBreathThrustSkillHandler()
                        .completesImmediately()
        );
    }

    @Test
    void pathDistanceUsesTheAuthoredHorizontalPiercingVolume() {
        assertEquals(
                0.5D,
                DragonBreathThrustSkillHandler.distancePointToSegment2D(
                        2.0D,
                        0.5D,
                        0.0D,
                        0.0D,
                        5.0D,
                        0.0D
                )
        );
        assertEquals(
                1.0D,
                DragonBreathThrustSkillHandler.distancePointToSegment2D(
                        6.0D,
                        0.0D,
                        0.0D,
                        0.0D,
                        5.0D,
                        0.0D
                )
        );
        assertEquals(
                1.0D,
                DragonBreathThrustSkillHandler.distancePointToSegment2D(
                        1.0D,
                        0.0D,
                        0.0D,
                        0.0D,
                        0.0D,
                        0.0D
                )
        );
    }

    @Test
    void invalidatedThrustStopsMovementWithoutDeletingLaterDashesOnCompletion() {
        assertTrue(DragonBreathThrustSkillHandler.shouldCancelMovement(
                SkillInstance.EndReason.INVALIDATED
        ));
        assertTrue(DragonBreathThrustSkillHandler.shouldCancelMovement(
                SkillInstance.EndReason.INTERRUPTED
        ));
        assertFalse(DragonBreathThrustSkillHandler.shouldCancelMovement(
                SkillInstance.EndReason.COMPLETED
        ));
        assertFalse(DragonBreathThrustSkillHandler.shouldCancelMovement(
                SkillInstance.EndReason.CASTER_UNAVAILABLE
        ));
    }
}
