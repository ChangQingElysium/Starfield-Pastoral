package com.stardew.craft.combat.skill;

import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaymoreFoldbackTrackerTest {
    @Test
    void returnStrikePreservesAuthoredDamageSlowAndPresentationContract() {
        SkillContext context =
                ClaymoreFoldbackTracker.createReturnContext(
                        "claymore_foldback"
                );

        assertEquals("claymore_foldback", context.getSkillId());
        assertEquals(SkillContext.SkillTier.MINOR, context.getTier());
        assertEquals(1.2F, context.getDamageMultiplier());
        assertFalse(context.isIgnoreDefense());
        assertFalse(context.isGuaranteedCrit());
        assertEquals(4.5D, ClaymoreFoldbackTracker.RETURN_TARGET_RANGE);
        assertEquals(5, ClaymoreFoldbackTracker.HIT_CONTEXT_LIFETIME_TICKS);
        assertEquals(40, ClaymoreFoldbackTracker.SLOW_DURATION_TICKS);
        assertEquals(0, ClaymoreFoldbackTracker.SLOW_AMPLIFIER);
        assertEquals(12, ClaymoreFoldbackTracker.RETURN_ANIMATION_TICKS);
    }

    @Test
    void delayedReturnFiresInclusivelyAndCancelsWhenCasterContextIsInvalid() {
        assertEquals(
                ClaymoreFoldbackTracker.TickDecision.WAIT,
                ClaymoreFoldbackTracker.tickDecision(
                        112L,
                        111L,
                        true,
                        true
                )
        );
        assertEquals(
                ClaymoreFoldbackTracker.TickDecision.FIRE,
                ClaymoreFoldbackTracker.tickDecision(
                        112L,
                        112L,
                        true,
                        true
                )
        );
        assertEquals(
                ClaymoreFoldbackTracker.TickDecision.CANCEL,
                ClaymoreFoldbackTracker.tickDecision(
                        112L,
                        110L,
                        false,
                        true
                )
        );
        assertEquals(
                ClaymoreFoldbackTracker.TickDecision.CANCEL,
                ClaymoreFoldbackTracker.tickDecision(
                        112L,
                        110L,
                        true,
                        false
                )
        );
    }

    @Test
    void delayedReturnCannotCrossDimensions() {
        assertTrue(ClaymoreFoldbackTracker.isSameDimension(
                Level.OVERWORLD,
                Level.OVERWORLD
        ));
        assertFalse(ClaymoreFoldbackTracker.isSameDimension(
                Level.OVERWORLD,
                Level.NETHER
        ));
    }

    @Test
    void invalidStoredTargetsFallBackInsteadOfReceivingTheReturnStrike() {
        assertTrue(ClaymoreFoldbackTracker.canReuseStoredTarget(
                false,
                true,
                true
        ));
        assertFalse(ClaymoreFoldbackTracker.canReuseStoredTarget(
                false,
                false,
                true
        ));
        assertFalse(ClaymoreFoldbackTracker.canReuseStoredTarget(
                false,
                true,
                false
        ));
        assertFalse(ClaymoreFoldbackTracker.canReuseStoredTarget(
                true,
                true,
                true
        ));
    }
}
