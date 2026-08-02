package com.stardew.craft.combat.skill;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SteelFalchionTickOwnershipContractTest {
    @Test
    void executionAndDetachedTicksAreSeparatelyIdempotent()
            throws IOException {
        String line = Files.readString(locate(javaPath(
                "combat/skill/handler/SteelFalchionLineExecutionState.java"
        )));
        String trace = Files.readString(locate(javaPath(
                "combat/skill/handler/SteelFalchionTraceExecutionState.java"
        )));
        String dots = Files.readString(locate(javaPath(
                "combat/skill/handler/SteelFalchionDotTracker.java"
        )));

        assertTrue(line.contains("implements SkillInstance.ExecutionState"));
        assertTrue(trace.contains("implements SkillInstance.ExecutionState"));
        assertFalse(line.contains("static final Map"));
        assertFalse(trace.contains("static final Map"));
        assertTrue(dots.contains("lastTick = Long.MIN_VALUE"));
        assertTrue(dots.contains("playerDots.lastTick == nowTick"));
        assertTrue(dots.contains("playerDots.lastTick = nowTick;"));
        assertTrue(dots.contains("tickDots(player, playerDots, nowTick)"));
        assertFalse(dots.contains("tickExecutionState"));
    }

    private static Path javaPath(String relative) {
        return Path.of("src", "main", "java", "com", "stardew", "craft")
                .resolve(relative);
    }

    private static Path locate(Path relative) throws IOException {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relative);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IOException("Cannot locate " + relative);
    }
}
