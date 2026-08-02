package com.stardew.craft.combat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeaponCriticalSemanticBoundaryContractTest {
    @Test
    void displayCriticalFlagIsOwnedOnlyByPresentation() throws IOException {
        Path combatRoot = mainCombatSourceRoot();
        try (var files = Files.walk(combatRoot)) {
            for (Path file : files
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList()) {
                String source = Files.readString(file);
                if (!source.contains(".displayCritical()")) {
                    continue;
                }
                assertEquals(
                        "LegacyWeaponHitPresentation.java",
                        file.getFileName().toString(),
                        () -> combatRoot.relativize(file)
                                + " must use DamageOutcome.isCrit() for gameplay"
                );
            }
        }

        String presentation = Files.readString(
                combatRoot.resolve("LegacyWeaponHitPresentation.java")
        );
        assertTrue(presentation.contains("hit.displayCritical()"));
    }

    private static Path mainCombatSourceRoot() throws IOException {
        Path relative = Path.of(
                "src", "main", "java", "com", "stardew", "craft",
                "combat"
        );
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relative);
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IOException("Cannot locate combat source root");
    }
}
