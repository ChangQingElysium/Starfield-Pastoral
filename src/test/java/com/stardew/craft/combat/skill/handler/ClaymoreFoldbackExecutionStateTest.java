package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.SkillContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaymoreFoldbackExecutionStateTest {
    @Test
    void returnStrikePreservesAuthoredDamageSlowAndPresentationContract() {
        SkillContext context =
                ClaymoreFoldbackExecutionState.createReturnContext();

        assertEquals("claymore_foldback_return", context.getSkillId());
        assertEquals(SkillContext.SkillTier.MINOR, context.getTier());
        assertEquals(1.2F, context.getDamageMultiplier());
        assertFalse(context.isIgnoreDefense());
        assertFalse(context.isGuaranteedCrit());
        assertEquals(
                4.5D,
                ClaymoreFoldbackSkillHandler.RETURN_TARGET_RANGE
        );
        assertEquals(
                5,
                ClaymoreFoldbackSkillHandler.HIT_CONTEXT_LIFETIME_TICKS
        );
        assertEquals(
                40,
                ClaymoreFoldbackSkillHandler.SLOW_DURATION_TICKS
        );
        assertEquals(0, ClaymoreFoldbackSkillHandler.SLOW_AMPLIFIER);
        assertEquals(
                12,
                ClaymoreFoldbackSkillHandler.RETURN_ANIMATION_TICKS
        );
    }

    @Test
    void delayedReturnFiresInclusivelyAfterTwelveTicks() {
        long fireTick = ClaymoreFoldbackExecutionState.returnFireTick(
                100L,
                ClaymoreFoldbackSkillHandler.RETURN_DELAY_TICKS
        );

        assertEquals(112L, fireTick);
        assertTrue(ClaymoreFoldbackExecutionState.shouldWait(
                fireTick,
                111L
        ));
        assertFalse(ClaymoreFoldbackExecutionState.shouldWait(
                fireTick,
                112L
        ));
        assertEquals(
                101L,
                ClaymoreFoldbackExecutionState.returnFireTick(100L, 0)
        );
    }

    @Test
    void invalidStoredTargetsFallBackInsteadOfReceivingTheReturnStrike() {
        assertTrue(ClaymoreFoldbackExecutionState.canReuseStoredTarget(
                false,
                true,
                true
        ));
        assertFalse(ClaymoreFoldbackExecutionState.canReuseStoredTarget(
                false,
                false,
                true
        ));
        assertFalse(ClaymoreFoldbackExecutionState.canReuseStoredTarget(
                false,
                true,
                false
        ));
        assertFalse(ClaymoreFoldbackExecutionState.canReuseStoredTarget(
                true,
                true,
                true
        ));
    }

    @Test
    void runtimeStatePreservesInitialAndReturnOrdering() throws IOException {
        Path handlerRoot = handlerRoot();
        String handler = Files.readString(handlerRoot.resolve(
                "ClaymoreFoldbackSkillHandler.java"
        ));
        String state = Files.readString(handlerRoot.resolve(
                "ClaymoreFoldbackExecutionState.java"
        ));

        assertTrue(handler.contains(
                "instance.initializeExecutionState("
        ));
        assertTrue(handler.contains(
                "instance.requireExecutionState("
        ));
        assertTrue(state.contains(
                "implements SkillInstance.ExecutionState"
        ));
        assertFalse(state.contains("static final Map"));
        assertFalse(state.contains("Map<UUID"));

        int initialHit = handler.indexOf("attackInitialTarget(context, target)");
        int stateRegistration = handler.indexOf(
                "instance.initializeExecutionState(",
                initialHit
        );
        int initialAnimation = handler.indexOf(
                "WeaponSkillAnimationDispatcher.sendSkillAnim(",
                stateRegistration
        );
        assertTrue(initialHit >= 0);
        assertTrue(stateRegistration > initialHit);
        assertTrue(initialAnimation > stateRegistration);

        int snapshot = state.indexOf(
                "context.weaponSnapshot()"
        );
        int damage = state.indexOf(
                "WeaponSkillDamage.apply(",
                snapshot
        );
        int bypass = state.indexOf(
                ".BYPASS_FOR_AUTHORED_SEQUENCE",
                damage
        );
        int returnAnimation = state.indexOf(
                "WeaponSkillAnimationDispatcher.sendSkillAnim(",
                bypass
        );
        int completion = state.indexOf(
                "return SkillTickResult.COMPLETE;",
                returnAnimation
        );
        assertTrue(snapshot >= 0);
        assertTrue(damage > snapshot);
        assertTrue(bypass > damage);
        assertTrue(returnAnimation > bypass);
        assertTrue(completion > returnAnimation);
        assertFalse(state.contains("target.invulnerableTime = 0;"));
        assertFalse(state.contains("target.hurtTime = 0;"));
        assertFalse(state.contains("WeaponSkillAnimationLock"));
    }

    private static Path handlerRoot() throws IOException {
        Path relative = Path.of(
                "src",
                "main",
                "java",
                "com",
                "stardew",
                "craft",
                "combat",
                "skill",
                "handler"
        );
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relative);
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IOException("Cannot locate handler source root");
    }
}
