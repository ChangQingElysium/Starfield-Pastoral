package com.stardew.craft.combat.skill;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PendingContextCleanupContractTest {
    private static final List<String> CENTRALIZED_TRACKER_SOURCES = List.of(
            "combat/skill/handler/FishcatchThrustExecutionState.java",
            "combat/skill/handler/CarvingThrustExecutionState.java",
            "combat/skill/handler/DwarfDaggerThrustExecutionState.java",
            "combat/skill/handler/IridiumNeedleThrustExecutionState.java",
            "combat/skill/handler/GalaxyDaggerThrustExecutionState.java",
            "combat/skill/handler/InfinityDaggerThrustExecutionState.java",
            "combat/skill/handler/ClaymoreFoldbackExecutionState.java",
            "combat/skill/handler/ObsidianCrackExecutionState.java",
            "combat/skill/handler/DarkSwordBloodMoonExecutionState.java",
            "combat/skill/handler/DwarfFortressExecutionState.java",
            "combat/skill/handler/HolyDomainExecutionState.java",
            "combat/skill/LavaKatanaMarkTracker.java",
            "combat/skill/handler/LavaKatanaReverbExecutionState.java",
            "combat/skill/handler/OssifiedExecutionState.java",
            "combat/skill/handler/SteelFalchionDotTracker.java",
            "combat/skill/handler/SteelFalchionTraceExecutionState.java",
            "combat/skill/TemperedFireRingTracker.java",
            "combat/skill/handler/TemplarJudgementExecutionState.java",
            "combat/skill/TemplarVowHandler.java",
            "combat/skill/handler/TemplarVowExecutionState.java",
            "combat/skill/WickedKrisPoisonTracker.java",
            "entity/effect/IceSpineEffectEntity.java",
            "entity/projectile/ElfBladeLeafEntity.java",
            "entity/projectile/TemperedBilletProjectileEntity.java",
            "entity/projectile/TideAnchorProjectileEntity.java"
    );

    @Test
    void migratedTrackersDelegateBindingAndCleanupToWeaponSkillDamage()
            throws IOException {
        Path sourceRoot = findJavaSourceRoot();
        for (String relative : CENTRALIZED_TRACKER_SOURCES) {
            String source = Files.readString(sourceRoot.resolve(relative));
            assertTrue(
                    source.contains("WeaponSkillDamage.apply"),
                    relative + " must use the centralized damage entry"
            );
            assertFalse(
                    source.contains("WeaponSkillContextStore.setPending("),
                    relative + " must not bind pending context locally"
            );
            assertFalse(
                    source.matches(
                            "(?s).*\\.hurt\\(.{0,240}?"
                                    + "1\\.0F\\s*\\).*"
                    ),
                    relative + " must not retain a raw sentinel hit"
            );
        }
    }

    @Test
    void steelFalchionDotAndBurstKeepTheirReleaseSnapshotOwnership()
            throws IOException {
        Path dotSource = findJavaSourceRoot().resolve(
                "combat/skill/handler/SteelFalchionDotTracker.java"
        );
        Path traceSource = findJavaSourceRoot().resolve(
                "combat/skill/handler/SteelFalchionTraceExecutionState.java"
        );
        String dots = Files.readString(dotSource);
        String trace = Files.readString(traceSource);
        assertTrue(
                dots.contains("dot.weaponSnapshot"),
                dotSource.toString()
        );
        assertTrue(
                trace.contains("weaponSnapshot"),
                traceSource.toString()
        );
        assertTrue(
                occurrences(
                        dots + trace,
                        "WeaponSkillDamage.apply("
                ) >= 4,
                "Steel Falchion DOT and burst must use centralized damage"
        );
    }

    @Test
    void obsidianResourceTrackerDoesNotOwnChildDamageDispatch()
            throws IOException {
        String tracker = Files.readString(findJavaSourceRoot().resolve(
                "combat/skill/ObsidianResonanceTracker.java"
        ));
        String appliedRules = Files.readString(findJavaSourceRoot().resolve(
                "combat/BuiltinWeaponPassiveAppliedHitRules.java"
        ));

        assertFalse(tracker.contains("WeaponSkillDamage.apply"));
        assertTrue(appliedRules.contains(
                "ObsidianResonanceTracker.consumeCharge("
        ));
        assertTrue(appliedRules.contains(
                "ObsidianResonanceTracker.createBonusContext("
        ));
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

    private static Path findJavaSourceRoot() throws IOException {
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
        throw new IOException(
                "Cannot locate Java sources from "
                        + Path.of("").toAbsolutePath()
        );
    }
}
