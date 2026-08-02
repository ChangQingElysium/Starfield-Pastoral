package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.SkillContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EternalCollapseExecutionStateTest {
    @Test
    void schedulesAuthoredStrikesAcrossTheSeventyTickField() {
        assertEquals(
                11L,
                EternalCollapseExecutionState.strikeInterval(70L, 6)
        );
        assertEquals(
                7L,
                EternalCollapseExecutionState.strikeInterval(70L, 10)
        );
        assertEquals(
                4L,
                EternalCollapseExecutionState.strikeInterval(3L, 1)
        );
        assertFalse(EternalCollapseExecutionState.shouldStrike(
                110L,
                111L,
                6
        ));
        assertTrue(EternalCollapseExecutionState.shouldStrike(
                111L,
                111L,
                6
        ));
        assertFalse(EternalCollapseExecutionState.shouldStrike(
                120L,
                111L,
                0
        ));
    }

    @Test
    void strikeContextPreservesMajorDamageAndCriticalBonus() {
        SkillContext context =
                EternalCollapseExecutionState.createStrikeContext(
                        "eternal_collapse",
                        3.0F,
                        0.15F
                );

        assertEquals("eternal_collapse", context.getSkillId());
        assertEquals(SkillContext.SkillTier.MAJOR, context.getTier());
        assertEquals(3.0F, context.getDamageMultiplier());
        assertEquals(0.15F, context.getCritChanceBonus());
        assertFalse(context.isGuaranteedCrit());
        assertFalse(context.isIgnoreDefense());
    }

    @Test
    void casterAvailabilityAndDimensionBothGateAdvancement() {
        assertTrue(EternalCollapseExecutionState.isValidContext(
                true,
                true
        ));
        assertFalse(EternalCollapseExecutionState.isValidContext(
                false,
                true
        ));
        assertFalse(EternalCollapseExecutionState.isValidContext(
                true,
                false
        ));
    }

    @Test
    void handlerUsesOnlyItsInstanceOwnedState() throws IOException {
        String handler = source("EternalCollapseSkillHandler.java");
        String state = source("EternalCollapseExecutionState.java");

        assertTrue(state.contains(
                "implements SkillInstance.ExecutionState"
        ));
        assertFalse(state.contains("static final Map"));
        assertFalse(state.contains("Map<UUID"));
        assertTrue(handler.contains(
                "instance.initializeExecutionState(executionState)"
        ));
        assertTrue(handler.contains(
                "instance.requireExecutionState("
        ));
        assertTrue(handler.contains(
                ".ifPresent(EternalCollapseExecutionState::cancel)"
        ));
        assertFalse(handler.contains("EternalCollapseTracker"));

        int consume = handler.indexOf("SingularityTracker.consumeAll(");
        int cooldown = handler.indexOf(
                "WeaponSkillRuntime.commitCooldown(",
                consume
        );
        int initialize = handler.indexOf(
                "instance.initializeExecutionState(executionState)",
                cooldown
        );
        int presentation = handler.indexOf(
                "executionState.startPresentation(",
                initialize
        );
        int animation = handler.indexOf(
                "WeaponSkillAnimationDispatcher.sendSkillAnim(",
                presentation
        );
        int lock = handler.indexOf(
                "WeaponSkillAnimationLock.setLock(",
                animation
        );
        assertTrue(consume >= 0);
        assertTrue(cooldown > consume);
        assertTrue(initialize > cooldown);
        assertTrue(presentation > initialize);
        assertTrue(animation > presentation);
        assertTrue(lock > animation);
    }

    @Test
    void fieldOrderSnapshotGateAndCancellationRemainAuthored()
            throws IOException {
        String state = source("EternalCollapseExecutionState.java");

        int disk = state.indexOf("new AccretionDiskPayload(");
        int core = state.indexOf("new SingularityCorePayload(", disk);
        int post = state.indexOf("new BlackHolePostPayload(", core);
        assertTrue(disk >= 0);
        assertTrue(core > disk);
        assertTrue(post > core);

        int pull = state.indexOf("pullTargets(context.player())");
        int regular = state.indexOf("if (shouldStrike(", pull);
        int ending = state.indexOf(
                "if (context.nowTick() >= endTick)",
                regular
        );
        int finisher = state.indexOf(
                "if (finalStrike)",
                ending
        );
        assertTrue(pull >= 0);
        assertTrue(regular > pull);
        assertTrue(ending > regular);
        assertTrue(finisher > ending);

        assertTrue(state.contains(
                "executionContext.weaponSnapshot();"
        ));
        assertTrue(state.contains(
                "WeaponSkillDamage.AttackGatePolicy.SKILL_DAMAGE"
        ));
        assertFalse(state.contains("RESPECT_AT_IMPACT"));
        assertTrue(state.contains(
                "WeaponSkillMovementArbiter.revokeCurrentIfPlayer(target)"
        ));

        String strike = method(
                state,
                "private void strike("
        );
        int damage = strike.indexOf("WeaponSkillDamage.apply(");
        int sound = strike.indexOf("level.playSound(", damage);
        int portal = strike.indexOf("ParticleTypes.PORTAL", sound);
        int smoke = strike.indexOf("ParticleTypes.SMOKE", portal);
        assertTrue(damage >= 0);
        assertTrue(sound > damage);
        assertTrue(portal > sound);
        assertTrue(smoke > portal);

        String cancel = method(state, "void cancel()");
        assertTrue(cancel.contains("settled = true;"));
        assertFalse(cancel.contains("strike("));
        assertFalse(cancel.contains("finalStrike"));
    }

    private static String source(String fileName) throws IOException {
        return Files.readString(handlerRoot().resolve(fileName));
    }

    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        if (start < 0) {
            return "";
        }
        int brace = source.indexOf('{', start);
        int depth = 0;
        for (int index = brace; index < source.length(); index++) {
            char character = source.charAt(index);
            if (character == '{') {
                depth++;
            } else if (character == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(start, index + 1);
                }
            }
        }
        return "";
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
