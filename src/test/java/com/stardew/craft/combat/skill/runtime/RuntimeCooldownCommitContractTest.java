package com.stardew.craft.combat.skill.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeCooldownCommitContractTest {
    @Test
    void representativeImmediateAndDelayedSkillsUseRuntimeCommit()
            throws IOException {
        assertRuntimeCommit("TetanusStrikeSkillHandler.java");
        assertRuntimeCommit("ForestBlessingSkillHandler.java");
        assertRuntimeCommit("BoneFractureSkillHandler.java");
        assertRuntimeCommit("BurglarShankSkillHandler.java");
        assertRuntimeCommit("CrescentSlashSkillHandler.java");
        assertRuntimeCommit("TreeBlessingSkillHandler.java");
        assertRuntimeCommit("DwarfDaggerThrustSkillHandler.java");
        assertRuntimeCommit("IronDirkThrustSkillHandler.java");
        assertRuntimeCommit("ClaymoreFoldbackSkillHandler.java");
        assertRuntimeCommit("CrystalDaggerLayerSkillHandler.java");
        assertRuntimeCommit("DesperatePlunderSkillHandler.java");
        assertRuntimeCommit("DragonBreathThrustSkillHandler.java");
        assertRuntimeCommit("DragontoothShivBreathSkillHandler.java");
        assertRuntimeCommit("DragontoothShivStabSkillHandler.java");
        assertRuntimeCommit("DwarfDaggerRushSkillHandler.java");
        assertRuntimeCommit("GalaxyDaggerStarstabSkillHandler.java");
        assertRuntimeCommit("HolySmiteSkillHandler.java");
        assertRuntimeCommit("LavaKatanaBrandSkillHandler.java");
        assertRuntimeCommit("OssifiedMarkSkillHandler.java");
        assertRuntimeCommit("YetiToothMarkSkillHandler.java");
    }

    @Test
    void runtimeDeduplicatesAndUsesTheReleaseWeaponSnapshot()
            throws IOException {
        String runtime = source(
                "combat/skill/runtime/WeaponSkillRuntime.java"
        );
        String cooldowns = source(
                "combat/skill/WeaponSkillCooldowns.java"
        );

        assertTrue(runtime.contains("instance.tryCommitCooldown()"));
        assertTrue(runtime.contains("context.weapon()"));
        assertTrue(cooldowns.contains("ItemStack releaseWeapon"));
        assertTrue(cooldowns.contains(
                "StardewEnchantments.has(releaseWeapon,"
        ));
    }

    @Test
    void deferredCooldownClaimsAndFreezesReleaseAdjustedDuration()
            throws IOException {
        String runtime = source(
                "combat/skill/runtime/WeaponSkillRuntime.java"
        );
        String deferred = source(
                "combat/skill/runtime/DeferredSkillCooldown.java"
        );

        assertTrue(runtime.contains("instance.tryDeferCooldown()"));
        assertTrue(runtime.contains("context.weapon()"));
        assertTrue(deferred.contains(
                "private final int appliedDurationTicks;"
        ));
        assertTrue(deferred.contains("instance.completeDeferredCooldown()"));
        assertTrue(deferred.contains("instance.abandonDeferredCooldown()"));
        assertTrue(runtime.contains("abandonDeferredCooldown("));
    }

    @Test
    void authoredCancellationAndChainReplacementEndDeferredTransactions()
            throws IOException {
        String femur = source(
                "combat/skill/handler/FemurSlamExecutionState.java"
        );
        String steel = source(
                "combat/skill/handler/SteelSpineFuryExecutionState.java"
        );
        String insect = source("combat/skill/InsectDashChainState.java");
        String cleanup = source("combat/CombatTrackerCleanup.java");

        assertTrue(femur.contains(
                "WeaponSkillRuntime.abandonDeferredCooldown("
        ));
        assertFalse(steel.contains(
                "WeaponSkillRuntime.abandonDeferredCooldown("
        ));
        assertTrue(steel.contains("commitCooldownAndClose("));
        assertTrue(insect.contains(
                "WeaponSkillRuntime.abandonDeferredCooldown("
        ));
        assertTrue(insect.contains(
                "WeaponSkillRuntime.commitDeferredCooldown("
        ));
        assertTrue(cleanup.contains("InsectDashChainState.cancel("));
    }

    @Test
    void everyHandlerRoutesCooldownSubmissionThroughRuntime()
            throws IOException {
        Set<String> directSubmitters = filesContaining(
                "combat/skill/handler",
                "WeaponSkillCooldowns.setCooldown("
        );

        assertEquals(Set.of(), directSubmitters);
    }

    @Test
    void everyHandlerEitherOwnsRuntimeCooldownOrIsExplicitlyCooldownFree()
            throws IOException {
        Set<String> withoutRuntimeCooldown = filesEndingWith(
                "combat/skill/handler",
                "SkillHandler.java"
        );
        withoutRuntimeCooldown.removeAll(filesContaining(
                "combat/skill/handler",
                "WeaponSkillRuntime.commitCooldown("
        ));
        withoutRuntimeCooldown.removeAll(filesContaining(
                "combat/skill/handler",
                "WeaponSkillRuntime.deferCooldown("
        ));

        assertEquals(
                Set.of(
                        "DragonBreathJudgementSkillHandler.java",
                        "ObsidianResonanceSkillHandler.java"
                ),
                withoutRuntimeCooldown
        );
    }

    @Test
    void remainingNonRuntimeCooldownOwnersStayExplicit()
            throws IOException {
        Set<String> directSubmitters = filesContaining(
                "combat/skill",
                "WeaponSkillCooldowns.setCooldown("
        );

        assertEquals(
                Set.of(
                        "WeaponSkillRuntime.java"
                ),
                directSubmitters
        );
    }

    @Test
    void silverFoldbackPersistsAndRestoresItsDeferredTransaction()
            throws IOException {
        String handler = source(
                "combat/skill/handler/SilverFoldbackSkillHandler.java"
        );
        String state = source(
                "combat/skill/SilverSaberFoldbackState.java"
        );
        String helper = source(
                "combat/skill/SilverSaberSkillHelper.java"
        );

        assertTrue(handler.contains("WeaponSkillRuntime.deferCooldown("));
        assertTrue(handler.contains("WeaponSkillRuntime.commitCooldown("));
        assertTrue(state.contains("TAG_COOLDOWN_ADJUSTED"));
        assertTrue(state.contains("restoreDeferredCooldown("));
        assertTrue(state.contains("restoreLegacyDeferredCooldown("));
        assertTrue(helper.contains("commitFoldbackCooldown("));
        assertFalse(helper.contains("WeaponSkillCooldowns.setCooldown("));
    }

    @Test
    void authoredCooldownResetIsAnExplicitRuntimeOverride()
            throws IOException {
        String executionState = source(
                "combat/skill/handler/DwarfDaggerThrustExecutionState.java"
        );
        String runtime = source(
                "combat/skill/runtime/WeaponSkillRuntime.java"
        );

        assertTrue(executionState.contains(
                "WeaponSkillRuntime.clearCooldown("
        ));
        assertFalse(executionState.contains(
                "WeaponSkillCooldowns.setCooldown("
        ));
        assertTrue(runtime.contains("WeaponSkillCooldowns.setCooldownUntil("));
    }

    private static void assertRuntimeCommit(String fileName)
            throws IOException {
        String handler = source("combat/skill/handler/" + fileName);
        assertTrue(handler.contains("WeaponSkillRuntime.commitCooldown("));
        assertFalse(handler.contains("WeaponSkillCooldowns.setCooldown("));
    }

    private static String source(String relativeSource) throws IOException {
        return Files.readString(sourcePath(relativeSource));
    }

    private static Set<String> filesContaining(
            String relativeDirectory,
            String needle
    ) throws IOException {
        Set<String> matches = new TreeSet<>();
        try (var paths = Files.walk(sourcePath(relativeDirectory))) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> {
                        try {
                            return Files.readString(path).contains(needle);
                        } catch (IOException exception) {
                            throw new IllegalStateException(exception);
                        }
                    })
                    .map(path -> path.getFileName().toString())
                    .forEach(matches::add);
        } catch (IllegalStateException exception) {
            if (exception.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw exception;
        }
        return matches;
    }

    private static Set<String> filesEndingWith(
            String relativeDirectory,
            String suffix
    ) throws IOException {
        Set<String> matches = new TreeSet<>();
        try (var paths = Files.walk(sourcePath(relativeDirectory))) {
            paths.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(suffix))
                    .forEach(matches::add);
        }
        return matches;
    }

    private static Path sourcePath(String relativeSource)
            throws IOException {
        Path relative = Path.of(
                "src",
                "main",
                "java",
                "com",
                "stardew",
                "craft"
        ).resolve(relativeSource);
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relative);
            if (Files.exists(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IOException("Cannot locate " + relative);
    }
}
