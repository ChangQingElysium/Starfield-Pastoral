package com.stardew.craft.combat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeaponDamageEntryContractTest {
    private static final Pattern RAW_ONE_POINT_HURT = Pattern.compile(
            "\\.hurt\\s*\\([\\s\\S]{0,240}?,\\s*1\\.0[Ff]\\s*\\)"
    );

    @Test
    void productionCombatHasNoRawOnePointPipelineTrigger()
            throws IOException {
        Path sourceRoot = mainSourceRoot();
        try (var files = Files.walk(sourceRoot)) {
            for (Path file : files
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList()) {
                String source = Files.readString(file);
                assertFalse(
                        RAW_ONE_POINT_HURT.matcher(source).find(),
                        sourceRoot.relativize(file).toString()
                );
            }
        }
    }

    @Test
    void clubSweepUsesTheSameSnapshotAwareDamageEntry()
            throws IOException {
        String source = Files.readString(
                mainSourceRoot()
                        .resolve("mixin")
                        .resolve("PlayerClubSweepAttackMixin.java")
        );
        assertTrue(source.contains(
                "WeaponDamageSnapshot.capture("
        ));
        assertTrue(source.contains(
                "WeaponSkillDamage.apply("
        ));
        assertTrue(source.contains(
                "SkillContext.normalAttack()"
        ));
        assertFalse(source.contains(".hurt("));
    }

    private static Path mainSourceRoot() throws IOException {
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
        throw new IOException("Cannot locate main source root");
    }
}
