package com.stardew.craft.combat.skill.handler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeOwnedTrackerTickContractTest {
    @Test
    void temperedQuenchAdvancesOnlyItsExecutionState()
            throws IOException {
        Path handler = findMainJavaRoot().resolve(Path.of(
                "com",
                "stardew",
                "craft",
                "combat",
                "skill",
                "handler",
                "TemperedQuenchSkillHandler.java"
        ));
        String tick = method(
                Files.readString(handler),
                "public SkillTickResult tick("
        );

        assertTrue(tick.contains(
                "TemperedQuenchExecutionState.class"
        ));
        assertTrue(tick.contains(".advance(context)"));
        assertTrue(!tick.contains("TemperedQuenchTracker"));
    }

    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0, "Missing method " + signature);
        int openingBrace = source.indexOf('{', start);
        assertTrue(openingBrace > start, "Missing method body " + signature);

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

    private static Path findMainJavaRoot() throws IOException {
        Path relative = Path.of("src", "main", "java");
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relative);
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IOException("Cannot locate " + relative);
    }

}
