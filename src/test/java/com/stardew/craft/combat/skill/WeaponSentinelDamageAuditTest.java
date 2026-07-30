package com.stardew.craft.combat.skill;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WeaponSentinelDamageAuditTest {
    private static final Set<String> RUNTIME_BOUND_IMPLICIT_SNAPSHOTS =
            Set.of(
                    "combat/skill/BrokenTridentThrustTracker.java",
                    "combat/skill/CarvingKnifeThrustTracker.java",
                    "combat/skill/ClaymoreFoldbackTracker.java",
                    "combat/skill/DwarfDaggerThrustTracker.java",
                    "combat/skill/DwarfFortressTracker.java",
                    "combat/skill/GalaxyDaggerThrustTracker.java",
                    "combat/skill/HolyBladeSanctuaryTracker.java",
                    "combat/skill/InfinityDaggerThrustTracker.java",
                    "combat/skill/IridiumNeedleThrustTracker.java",
                    "combat/skill/ObsidianCrackTracker.java",
                    "combat/skill/TemplarJudgementTracker.java"
            );

    private static final Set<String> NON_SKILL_SENTINELS = Set.of(
            "gametest/ApiContractGameTests.java",
            "mixin/PlayerClubSweepAttackMixin.java"
    );

    private static final Set<String> FINAL_STAGE_C_MIGRATIONS = Set.of(
            "combat/skill/TemperedFireRingTracker.java",
            "combat/skill/TemplarJudgementTracker.java",
            "combat/skill/WickedKrisPoisonTracker.java",
            "entity/effect/IceSpineEffectEntity.java",
            "entity/projectile/ElfBladeLeafEntity.java",
            "entity/projectile/TemperedBilletProjectileEntity.java",
            "entity/projectile/TideAnchorProjectileEntity.java"
    );

    @Test
    void everyDirectSkillSentinelOwnsContextSnapshotAndCleanup()
            throws IOException {
        Path sourceRoot = locateMainSourceRoot();
        try (var paths = Files.walk(sourceRoot)) {
            for (Path file : paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList()) {
                String source = Files.readString(file);
                int sentinelCalls = occurrences(source, "playerAttack(");
                if (sentinelCalls == 0) {
                    continue;
                }

                String relative = sourceRoot.relativize(file)
                        .toString()
                        .replace('\\', '/');
                if (NON_SKILL_SENTINELS.contains(relative)) {
                    continue;
                }

                assertTrue(
                        source.contains("SkillContext"),
                        relative + " must bind a SkillContext"
                );
                assertTrue(
                        source.contains(
                                "WeaponSkillContextStore.setPending"
                        ),
                        relative + " must bind its pending hit"
                );
                assertTrue(
                        occurrences(source, "finally {") >= sentinelCalls,
                        relative + " must clean every rejected sentinel hit"
                );
                assertTrue(
                        source.contains("WeaponDamageSnapshot")
                                || source.contains(
                                        "context.weaponSnapshot()"
                                )
                                || RUNTIME_BOUND_IMPLICIT_SNAPSHOTS
                                        .contains(relative),
                        relative + " must own a release snapshot or remain "
                                + "inside its matching active runtime"
                );
            }
        }
    }

    @Test
    void immediateRuntimeHandlersCleanEveryAttackAttempt()
            throws IOException {
        Path handlerRoot = locateMainSourceRoot()
                .resolve("combat")
                .resolve("skill")
                .resolve("handler");
        try (var paths = Files.list(handlerRoot)) {
            for (Path file : paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList()) {
                String source = Files.readString(file);
                int attackCalls = occurrences(
                        source,
                        "context.player().attack("
                );
                if (attackCalls == 0) {
                    continue;
                }

                assertTrue(
                        source.contains("RuntimeWeaponSkillHandler"),
                        file.getFileName()
                                + " must execute inside WeaponSkillRuntime"
                );
                assertTrue(
                        occurrences(
                                source,
                                "WeaponSkillContextStore.setPending("
                        ) >= attackCalls,
                        file.getFileName()
                                + " must bind every immediate attack"
                );
                assertTrue(
                        occurrences(source, "finally {") >= attackCalls,
                        file.getFileName()
                                + " must clean every rejected immediate hit"
                );
            }
        }
    }

    @Test
    void everyCentralizedSkillDamageCallOwnsAReleaseWeapon()
            throws IOException {
        Path sourceRoot = locateMainSourceRoot();
        try (var paths = Files.walk(sourceRoot)) {
            for (Path file : paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList()) {
                String source = Files.readString(file);
                if (!source.contains("WeaponSkillDamage.apply(")) {
                    continue;
                }

                String relative = sourceRoot.relativize(file)
                        .toString()
                        .replace('\\', '/');
                assertTrue(
                        source.contains("SkillContext"),
                        relative + " must construct a SkillContext"
                );
                assertTrue(
                        source.contains("WeaponDamageSnapshot")
                                || source.contains(
                                        "context.weaponSnapshot()"
                                )
                                || RUNTIME_BOUND_IMPLICIT_SNAPSHOTS
                                        .contains(relative),
                        relative + " must own a release snapshot or remain "
                                + "inside its matching active runtime"
                );
            }
        }
    }

    @Test
    void finalStageCMigrationsOnlyUseTheCentralizedSentinel()
            throws IOException {
        Path sourceRoot = locateMainSourceRoot();
        for (String relative : FINAL_STAGE_C_MIGRATIONS) {
            String source = Files.readString(sourceRoot.resolve(relative));
            assertTrue(
                    source.contains("WeaponSkillDamage.apply("),
                    relative + " must use the centralized damage entry"
            );
            assertTrue(
                    !source.contains("playerAttack("),
                    relative + " must not emit a direct sentinel"
            );
            assertTrue(
                    !source.contains(
                            "WeaponSkillContextStore.setPending("
                    ),
                    relative + " must not own pending cleanup locally"
            );
        }
    }

    @Test
    void centralCombatEventDoesNotOwnNestedPlayerAttackSentinels()
            throws IOException {
        String source = Files.readString(
                locateMainSourceRoot()
                        .resolve("combat")
                        .resolve("WeaponCombatEvents.java")
        );

        assertTrue(
                occurrences(source, "WeaponSkillDamage.apply(") == 6,
                "all six reactive child hits must use the centralized entry"
        );
        assertTrue(
                !source.contains("player.attack(target)"),
                "central combat events must not recursively call Player.attack"
        );
        assertTrue(
                !source.contains("WeaponSkillContextStore.setPending("),
                "central combat events must delegate pending ownership"
        );
        assertTrue(
                !source.contains("clearUnconsumedSkillContext("),
                "central combat events must delegate rejected-hit cleanup"
        );
    }

    @Test
    void runtimePublishesTheReleaseSnapshotBeforeBegin()
            throws IOException {
        String runtime = Files.readString(
                locateMainSourceRoot()
                        .resolve("combat")
                        .resolve("skill")
                        .resolve("runtime")
                        .resolve("WeaponSkillRuntime.java")
        );

        int publish = runtime.indexOf("ACTIVE.put(");
        int begin = runtime.indexOf("handler.begin(context, instance)");
        assertTrue(publish >= 0 && begin > publish);
        assertTrue(runtime.contains("context.weaponSnapshot()"));
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

    private static Path locateMainSourceRoot() throws IOException {
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
        throw new IOException("Cannot locate " + relative);
    }
}
