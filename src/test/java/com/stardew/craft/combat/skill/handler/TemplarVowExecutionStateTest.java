package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.SkillContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemplarVowExecutionStateTest {
    @Test
    void vowWindowKeepsItsInclusiveAuthoredEndTick() {
        assertTrue(TemplarVowExecutionState.isWithinActiveWindow(
                140L,
                140L
        ));
        assertFalse(TemplarVowExecutionState.isWithinActiveWindow(
                141L,
                140L
        ));
    }

    @Test
    void counterAndExpiryUseTheAuthoredMinorStrikeContexts() {
        SkillContext counter = TemplarVowSkillHandler.createStrikeContext(
                TemplarVowSkillHandler.COUNTER_DAMAGE_MULTIPLIER
        );
        SkillContext expiry = TemplarVowSkillHandler.createStrikeContext(
                TemplarVowSkillHandler.EXPIRE_SLASH_DAMAGE_MULTIPLIER
        );

        assertEquals("templar_vow", counter.getSkillId());
        assertEquals(SkillContext.SkillTier.MINOR, counter.getTier());
        assertEquals(1.10F, counter.getDamageMultiplier());
        assertEquals(0.80F, expiry.getDamageMultiplier());
        assertFalse(counter.isIgnoreDefense());
        assertFalse(counter.isGuaranteedCrit());
    }

    @Test
    void incomingDamageConsumesOnlyTheExactRuntimeExecution()
            throws IOException {
        String handler = source(
                "combat/skill/handler/TemplarVowSkillHandler.java"
        );
        String state = source(
                "combat/skill/handler/TemplarVowExecutionState.java"
        );
        String incoming = source(
                "combat/skill/TemplarVowHandler.java"
        );

        assertTrue(handler.contains(
                "WeaponSkillRuntime.activeExecutionState("
        ));
        assertTrue(handler.contains(
                "BuiltinWeaponSkillHandlers.TEMPLAR_VOW"
        ));
        assertTrue(handler.contains("TemplarVowExecutionState.class"));
        assertTrue(incoming.contains(
                "TemplarVowSkillHandler.consumeCounter("
        ));
        assertTrue(incoming.contains(
                "TemplarVowSkillHandler.finishCounter(player, nowTick)"
        ));
        assertFalse(incoming.contains("TemplarVowTracker"));
        assertFalse(handler.contains("TemplarVowTracker"));
        assertTrue(state.contains(
                "implements SkillInstance.ExecutionState"
        ));
        assertTrue(state.contains(
                "private final WeaponDamageSnapshot weaponSnapshot;"
        ));
        assertTrue(state.contains(
                "private final DeferredSkillCooldown cooldown;"
        ));
        assertFalse(state.contains("static final Map"));
    }

    @Test
    void beginCounterExpiryAndFinishKeepTheirAuthoredOrder()
            throws IOException {
        String handler = source(
                "combat/skill/handler/TemplarVowSkillHandler.java"
        );
        String state = source(
                "combat/skill/handler/TemplarVowExecutionState.java"
        );
        String incoming = source(
                "combat/skill/TemplarVowHandler.java"
        );

        int defer = handler.indexOf("WeaponSkillRuntime.deferCooldown(");
        int initialize = handler.indexOf(
                "instance.initializeExecutionState(executionState)",
                defer
        );
        int committed = handler.indexOf(
                "instance.registerCommittedEffect(",
                initialize
        );
        int start = handler.indexOf("executionState.start(", committed);
        int animation = handler.indexOf(
                "WeaponSkillAnimationDispatcher.sendSkillAnim(",
                start
        );
        assertTrue(defer >= 0);
        assertTrue(initialize > defer);
        assertTrue(committed > initialize);
        assertTrue(start > committed);
        assertTrue(animation > start);

        int consume = incoming.indexOf(
                "TemplarVowSkillHandler.consumeCounter("
        );
        int mitigation = incoming.indexOf("event.setAmount(0.0f)", consume);
        int sound = incoming.indexOf("SoundEvents.SHIELD_BLOCK", mitigation);
        int swing = incoming.indexOf(
                "player.swing(InteractionHand.MAIN_HAND, true)",
                sound
        );
        int damage = incoming.indexOf("WeaponSkillDamage.apply(", swing);
        int finish = incoming.indexOf(
                "TemplarVowSkillHandler.finishCounter(player, nowTick)",
                damage
        );
        assertTrue(consume >= 0);
        assertTrue(mitigation > consume);
        assertTrue(sound > mitigation);
        assertTrue(swing > sound);
        assertTrue(damage > swing);
        assertTrue(finish > damage);

        int advance = state.indexOf("SkillTickResult advance(");
        int expiryCall = state.indexOf("applyExpirySlash(context);", advance);
        int expirySwing = state.indexOf(
                "context.player().swing(InteractionHand.MAIN_HAND, true)",
                expiryCall
        );
        int settleCall = state.indexOf(
                "settle(context.player(), context.nowTick(), true);",
                expirySwing
        );
        int shelter = state.indexOf("ModMobEffects.SHELTER.get()");
        int target = state.indexOf("findTargetEntity(", shelter);
        int expiryDamage = state.indexOf("WeaponSkillDamage.apply(", target);
        int cooldown = state.indexOf(
                "WeaponSkillRuntime.commitDeferredCooldown("
        );
        assertTrue(advance >= 0);
        assertTrue(expiryCall > advance);
        assertTrue(expirySwing > expiryCall);
        assertTrue(settleCall > expirySwing);
        assertTrue(shelter >= 0);
        assertTrue(target > shelter);
        assertTrue(expiryDamage > target);
        assertTrue(cooldown > expirySwing);
    }

    private static String source(String relativePath) throws IOException {
        Path relative = Path.of(
                "src",
                "main",
                "java",
                "com",
                "stardew",
                "craft"
        ).resolve(relativePath);
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relative);
            if (Files.isRegularFile(candidate)) {
                return Files.readString(candidate);
            }
            current = current.getParent();
        }
        throw new IOException("Cannot locate " + relative);
    }
}
