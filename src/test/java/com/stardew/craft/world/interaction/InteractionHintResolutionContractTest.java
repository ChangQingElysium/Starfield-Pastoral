package com.stardew.craft.world.interaction;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InteractionHintResolutionContractTest {
    @Test
    void blockHintsDoNotGuessActionsFromDeclaredClickMethods()
            throws IOException {
        String source = normalizedServiceSource();

        assertFalse(source.contains("getDeclaredMethod("));
        assertFalse(source.contains("ACTION_METHODS"));
        assertFalse(source.contains("declaresOverride("));
        assertFalse(source.contains("tag.contains(\"interaction\")"));
        assertFalse(source.contains("entity instanceof Interaction ||"));
        assertTrue(source.contains(
                "state.getMenuProvider(level, pos) != null"));
    }

    private static String normalizedServiceSource() throws IOException {
        Path relative = Path.of(
                "src", "main", "java", "com", "stardew", "craft",
                "world", "interaction", "InteractionHintService.java");
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relative);
            if (Files.isRegularFile(candidate)) {
                return Files.readString(candidate)
                        .replaceAll("\\s+", " ");
            }
            current = current.getParent();
        }
        throw new IOException("Cannot locate " + relative);
    }
}
