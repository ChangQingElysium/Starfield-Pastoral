package com.stardew.craft.combat.skill.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WeaponSkillMigrationAuditTest {
    private static final Path WEAPON_SOURCE_RELATIVE_PATH = Path.of(
            "src",
            "main",
            "java",
            "com",
            "stardew",
            "craft",
            "item",
            "weapon"
    );
    private static final Set<String> LEGACY_ITEM_SOURCES = Set.of(
            "StardewWeaponItem.java",
            "StardewDaggerItem.java",
            "StardewClubItem.java"
    );

    @Test
    void everyDefinedSkillHasExactlyOneExecutionPath() {
        WeaponSkillMigrationAudit.Snapshot snapshot =
                WeaponSkillMigrationAudit.snapshot();

        Set<ResourceLocation> classified = new LinkedHashSet<>(snapshot.runtimeSkills());
        classified.addAll(snapshot.legacySkills());

        assertFalse(snapshot.definitions().isEmpty());
        assertFalse(snapshot.runtimeSkills().isEmpty());
        assertEquals(snapshot.definitions().keySet(), classified);

        Set<ResourceLocation> overlap = new LinkedHashSet<>(snapshot.runtimeSkills());
        overlap.retainAll(snapshot.legacySkills());
        assertEquals(Set.of(), overlap);
    }

    @Test
    void runtimeHandlersNeverRetainAnExplicitLegacyItemBranch() throws IOException {
        WeaponSkillMigrationAudit.Snapshot snapshot =
                WeaponSkillMigrationAudit.snapshot();
        String legacySources = readLegacyItemSources();
        Set<ResourceLocation> explicitLegacyBranches = new LinkedHashSet<>();

        for (ResourceLocation skillId : snapshot.definitions().keySet()) {
            if (hasExplicitLegacyBranch(legacySources, skillId)) {
                explicitLegacyBranches.add(skillId);
            }
        }

        Set<ResourceLocation> duplicated = new LinkedHashSet<>(snapshot.runtimeSkills());
        duplicated.retainAll(explicitLegacyBranches);
        assertEquals(Set.of(), duplicated, () ->
                "Runtime skills still have explicit legacy item branches: " + duplicated);
        assertEquals(
                snapshot.legacySkills(),
                explicitLegacyBranches,
                () -> "Definitions and explicit legacy branches are out of sync"
        );
    }

    @Test
    void snapshotCollectionsAreImmutable() {
        WeaponSkillMigrationAudit.Snapshot snapshot =
                WeaponSkillMigrationAudit.snapshot();
        ResourceLocation firstSkill = snapshot.definitions().keySet().iterator().next();

        assertThrows(
                UnsupportedOperationException.class,
                () -> snapshot.definitions().clear()
        );
        assertThrows(
                UnsupportedOperationException.class,
                () -> snapshot.definitions().get(firstSkill).clear()
        );
        assertThrows(
                UnsupportedOperationException.class,
                () -> snapshot.runtimeSkills().clear()
        );
        assertThrows(
                UnsupportedOperationException.class,
                () -> snapshot.legacySkills().clear()
        );
    }

    private static String readLegacyItemSources() throws IOException {
        Path sourceRoot = findWeaponSourceRoot();
        StringBuilder sources = new StringBuilder();
        for (String sourceName : LEGACY_ITEM_SOURCES) {
            sources.append(Files.readString(sourceRoot.resolve(sourceName)))
                    .append('\n');
        }
        return sources.toString();
    }

    private static Path findWeaponSourceRoot() throws IOException {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(WEAPON_SOURCE_RELATIVE_PATH);
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IOException(
                "Cannot locate weapon sources from " + Path.of("").toAbsolutePath()
        );
    }

    private static boolean hasExplicitLegacyBranch(
            String source,
            ResourceLocation skillId
    ) {
        String literal = Pattern.quote("\"" + skillId.getPath() + "\"");
        Pattern explicitBranch = Pattern.compile(
                literal
                        + "\\s*\\.equals\\(\\s*skill"
                        + "(?:Id|\\.getId\\(\\))\\s*\\)"
        );
        return explicitBranch.matcher(source).find();
    }
}
