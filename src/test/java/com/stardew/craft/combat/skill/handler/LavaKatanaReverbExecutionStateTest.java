package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.SkillContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LavaKatanaReverbExecutionStateTest {
    @Test
    void activeWindowRemainsInclusiveAndDimensionBound() {
        LavaKatanaReverbExecutionState state =
                new LavaKatanaReverbExecutionState(
                        Level.OVERWORLD,
                        100L,
                        80
                );

        assertTrue(state.isActive(180L, Level.OVERWORLD));
        assertFalse(state.isActive(181L, Level.OVERWORLD));
        assertFalse(state.isActive(120L, Level.NETHER));
        state.cancel();
        assertFalse(state.isActive(120L, Level.OVERWORLD));
    }

    @Test
    void finisherPreservesBurnJumpAndHeatFormula() {
        assertEquals(
                3.85F,
                LavaKatanaReverbExecutionState.finisherDamageMultiplier(
                        40,
                        5
                ),
                0.0001F
        );
        SkillContext finisher =
                LavaKatanaReverbExecutionState.createFinisherContext(3.85F);
        assertEquals(
                LavaKatanaReverbExecutionState.FINISHER_SKILL_ID,
                finisher.getSkillId()
        );
        assertEquals(SkillContext.SkillTier.MAJOR, finisher.getTier());
        assertEquals(3.85F, finisher.getDamageMultiplier());
        assertEquals(
                5,
                LavaKatanaReverbExecutionState.HIT_CONTEXT_LIFETIME_TICKS
        );
        assertEquals(
                1.5F,
                LavaKatanaReverbExecutionState.FINISHER_BASE_SCALAR
        );
        assertEquals(
                0.05F,
                LavaKatanaReverbExecutionState.FINISHER_HEAT_SCALAR
        );
    }

    @Test
    void markTrackerObservesOnlyTheRuntimeOwnedTypedState()
            throws IOException {
        String handler = source(
                "combat/skill/handler/LavaKatanaReverbSkillHandler.java"
        );
        String markTracker = source(
                "combat/skill/LavaKatanaMarkTracker.java"
        );
        String runtime = source(
                "combat/skill/runtime/WeaponSkillRuntime.java"
        );

        assertTrue(handler.contains(
                "WeaponSkillRuntime.activeExecutionState("
        ));
        assertTrue(handler.contains(
                "BuiltinWeaponSkillHandlers.LAVA_KATANA_REVERB"
        ));
        assertTrue(handler.contains(
                "LavaKatanaReverbExecutionState.class"
        ));
        assertEquals(
                2,
                occurrences(
                        markTracker,
                        "LavaKatanaReverbSkillHandler.isActive("
                )
        );
        assertFalse(markTracker.contains("LavaKatanaReverbTracker"));
        assertTrue(runtime.contains(
                "instance.phase() != SkillInstance.Phase.ACTIVE"
        ));
        assertTrue(runtime.contains(
                "!instance.casterId().equals(casterId)"
        ));
        assertTrue(runtime.contains(
                "!instance.skillId().equals(skillId)"
        ));
    }

    @Test
    void completionOwnsFinisherAndCancellationCannotTriggerIt()
            throws IOException {
        String state = source(
                "combat/skill/handler/LavaKatanaReverbExecutionState.java"
        );
        String handler = source(
                "combat/skill/handler/LavaKatanaReverbSkillHandler.java"
        );

        int activeWindow = state.indexOf(
                "isWithinActiveWindow(context.nowTick(), endTick)"
        );
        int finish = state.indexOf("finish(context)", activeWindow);
        int settled = state.indexOf("settled = true", finish);
        assertTrue(activeWindow >= 0);
        assertTrue(finish > activeWindow);
        assertTrue(settled > finish);
        assertTrue(state.contains(
                "context.weaponSnapshot()"
        ));
        assertTrue(state.contains(
                "LavaKatanaMarkTracker.clearMark(target)"
        ));
        assertFalse(method(state, "void cancel()").contains("finish("));
        assertFalse(handler.contains("LavaKatanaReverbTracker"));
        assertFalse(state.contains("static final Map"));
    }

    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0, "Missing method " + signature);
        int openingBrace = source.indexOf('{', start);
        int depth = 0;
        for (int index = openingBrace; index < source.length(); index++) {
            char current = source.charAt(index);
            if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(start, index + 1);
                }
            }
        }
        throw new AssertionError("Unclosed method " + signature);
    }

    private static int occurrences(String source, String token) {
        int count = 0;
        int index = source.indexOf(token);
        while (index >= 0) {
            count++;
            index = source.indexOf(token, index + token.length());
        }
        return count;
    }

    private static String source(String relative) throws IOException {
        return Files.readString(javaRoot().resolve(relative));
    }

    private static Path javaRoot() throws IOException {
        Path relative = Path.of(
                "src",
                "main",
                "java",
                "com",
                "stardew",
                "craft"
        );
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relative);
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IOException("Cannot locate Java source root");
    }
}
