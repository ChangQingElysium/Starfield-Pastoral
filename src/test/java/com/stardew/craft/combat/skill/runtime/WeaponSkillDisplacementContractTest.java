package com.stardew.craft.combat.skill.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeaponSkillDisplacementContractTest {
    private static final List<TeleportHelper> CASTER_TELEPORTS = List.of(
            new TeleportHelper(
                    "SilverFoldbackSkillHandler.java",
                    "private static void teleport("
            ),
            new TeleportHelper(
                    "GalaxyDaggerStarleapSkillHandler.java",
                    "private static void teleportPlayer("
            ),
            new TeleportHelper(
                    "InfinityDaggerSingularityBackstabSkillHandler.java",
                    "private static void teleportPlayer("
            ),
            new TeleportHelper(
                    "IronDirkThrustSkillHandler.java",
                    "private static void teleportPlayer("
            ),
            new TeleportHelper(
                    "WindSpireThrustSkillHandler.java",
                    "private static void teleportToTargetFront("
            ),
            new TeleportHelper(
                    "CrystalDaggerLayerSkillHandler.java",
                    "private static void teleportToTargetFront("
            )
    );
    private static final List<String> MOVEMENT_SKILL_HANDLERS = List.of(
            "SilverFoldbackSkillHandler.java",
            "GalaxyDaggerStarleapSkillHandler.java",
            "InfinityDaggerSingularityBackstabSkillHandler.java",
            "IronDirkThrustSkillHandler.java",
            "WindSpireThrustSkillHandler.java",
            "CrystalDaggerLayerSkillHandler.java",
            "DragonBreathThrustSkillHandler.java",
            "DwarfDaggerThrustSkillHandler.java",
            "InsectDashSkillHandler.java",
            "StartrailRiftSkillHandler.java"
    );

    @Test
    void oneShotCasterTeleportsRevokeMovementImmediatelyBeforeDisplacement()
            throws IOException {
        for (TeleportHelper helper : CASTER_TELEPORTS) {
            String source = source(
                    "combat/skill/handler/" + helper.file
            );
            String method = method(source, helper.signature);
            int revoke = method.indexOf(
                    "WeaponSkillMovementArbiter.revokeCurrent(serverPlayer)"
            );
            int teleport = method.indexOf("player.teleportTo(");

            assertTrue(revoke >= 0, helper.file);
            assertTrue(teleport > revoke, helper.file);
        }

        String ironDirk = source(
                "combat/skill/handler/IronDirkThrustSkillHandler.java"
        );
        assertEquals(
                2,
                occurrences(
                        ironDirk,
                        "teleportPlayer(context.player(),"
                )
        );
    }

    @Test
    void hostilePlayerPullsRevokeTheVictimsCurrentMovement()
            throws IOException {
        String obsidian = method(
                source(
                        "combat/skill/handler/"
                                + "ObsidianCrackExecutionState.java"
                ),
                "private void explode("
        );
        String anchor = method(
                source("entity/projectile/TideAnchorProjectileEntity.java"),
                "private void handleImpact("
        );

        assertOrdered(
                obsidian,
                "target instanceof ServerPlayer targetPlayer",
                "WeaponSkillMovementArbiter.revokeCurrent(targetPlayer)",
                "target.teleportTo("
        );
        assertOrdered(
                anchor,
                "marked instanceof ServerPlayer serverPlayer",
                "WeaponSkillMovementArbiter.revokeCurrent(serverPlayer)",
                "marked.teleportTo("
        );
    }

    @Test
    void everyCombatOrProjectileTeleportHasAnAuditedOwner()
            throws IOException {
        Path mainJava = findMainJavaRoot();
        List<Path> teleportOwners;
        try (var sources = Files.walk(mainJava)) {
            teleportOwners = sources
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> {
                        Path relative = mainJava.relativize(path);
                        String normalized = relative.toString()
                                .replace('\\', '/');
                        return normalized.startsWith(
                                "com/stardew/craft/combat/skill/"
                        ) || normalized.startsWith(
                                "com/stardew/craft/entity/projectile/"
                        );
                    })
                    .filter(path -> {
                        try {
                            return Files.readString(path).contains(
                                    "teleportTo("
                            );
                        } catch (IOException exception) {
                            throw new SourceReadException(exception);
                        }
                    })
                    .map(mainJava::relativize)
                    .sorted()
                    .toList();
        } catch (SourceReadException exception) {
            throw exception.ioException;
        }

        assertEquals(
                List.of(
                        Path.of("com/stardew/craft/combat/skill/"
                                + "DashMovementTracker.java"),
                        Path.of("com/stardew/craft/combat/skill/handler/"
                                + "CrystalDaggerLayerSkillHandler.java"),
                        Path.of("com/stardew/craft/combat/skill/handler/"
                                + "DwarfDaggerThrustExecutionState.java"),
                        Path.of("com/stardew/craft/combat/skill/handler/"
                                + "GalaxyDaggerStarleapSkillHandler.java"),
                        Path.of("com/stardew/craft/combat/skill/handler/"
                                + "InfinityDaggerSingularityBackstabSkillHandler.java"),
                        Path.of("com/stardew/craft/combat/skill/handler/"
                                + "IronDirkThrustSkillHandler.java"),
                        Path.of("com/stardew/craft/combat/skill/handler/"
                                + "ObsidianCrackExecutionState.java"),
                        Path.of("com/stardew/craft/combat/skill/handler/"
                                + "SilverFoldbackSkillHandler.java"),
                        Path.of("com/stardew/craft/combat/skill/handler/"
                                + "WindSpireThrustSkillHandler.java"),
                        Path.of("com/stardew/craft/entity/projectile/"
                                + "TideAnchorProjectileEntity.java")
                ),
                teleportOwners
        );
    }

    @Test
    void movementOwnersDoNotRevokeTheirOwnFallbackTeleport()
            throws IOException {
        String dash = source("combat/skill/DashMovementTracker.java");
        String dwarf = source(
                "combat/skill/handler/DwarfDaggerThrustExecutionState.java"
        );

        assertTrue(dash.contains("player.teleportTo("));
        assertFalse(dash.contains("revokeCurrent("));
        assertTrue(dwarf.contains("player.teleportTo("));
        assertFalse(dwarf.contains("revokeCurrent("));
    }

    @Test
    void arbiterDropsTheOldClaimBeforeNotifyingItsOwner()
            throws IOException {
        String arbiter = source(
                "combat/skill/runtime/WeaponSkillMovementArbiter.java"
        );
        String revoke = method(
                arbiter,
                "public static synchronized boolean revokeCurrent("
        );

        assertOrdered(
                revoke,
                "Claim previous = ACTIVE.remove(player.getUUID())",
                "previous.owner().onMovementRevoked(player)"
        );
    }

    @Test
    void hardFreezeRevokesMovementBeforePostServerMoversAdvance()
            throws IOException {
        String freeze = source("combat/skill/YetiFreezeTracker.java");
        String apply = method(
                freeze,
                "public static int applyPreAdjusted("
        );
        assertOrdered(
                apply,
                "tag.putLong(TAG_END_TICK",
                "WeaponSkillMovementArbiter.revokeCurrent(player)",
                "target.setDeltaMovement(0.0, 0.0, 0.0)"
        );

        String postServer = method(
                source("combat/skill/runtime/WeaponSkillPostServerRuntime.java"),
                "public static void onServerTick("
        );
        assertOrdered(
                postServer,
                "WeaponSkillMovementControl.isLocked(player, nowTick)",
                "WeaponSkillMovementArbiter.revokeCurrent(player)",
                "DashMovementTracker.tickServer(event.getServer())",
                "WeaponSkillRuntime.tickPostServer(event.getServer())"
        );
    }

    @Test
    void hardFreezeRejectsEveryAuthoredCasterMovementBeforeSideEffects()
            throws IOException {
        for (String file : MOVEMENT_SKILL_HANDLERS) {
            String source = source("combat/skill/handler/" + file);
            String validate = method(
                    source,
                    "public SkillValidation validate("
            );
            String begin = method(source, "public void begin(");
            assertTrue(
                    validate.contains(
                            "WeaponSkillMovementControl.isLocked("
                    ),
                    "validate: " + file
            );
            assertTrue(
                    begin.contains(
                            "WeaponSkillMovementControl.isLocked("
                    ) || begin.contains("requireMovementUnlocked(context"),
                    "begin: " + file
            );
        }

        String dashStart = method(
                source("combat/skill/DashMovementTracker.java"),
                "public static Handle startExact("
        );
        assertOrdered(
                dashStart,
                "WeaponSkillMovementControl.isLocked(player, nowTick)",
                "WeaponSkillMovementArbiter.claim(player, state)"
        );
    }

    @Test
    void allContinuousForcedPullsTakeOwnershipFromPlayerMovement()
            throws IOException {
        List<String> pullOwners = List.of(
                "combat/skill/handler/EternalCollapseExecutionState.java",
                "combat/skill/handler/OssifiedExecutionState.java",
                "combat/skill/handler/SingularityEvolveExecutionState.java",
                "combat/skill/handler/TideReelSkillHandler.java"
        );
        for (String file : pullOwners) {
            String source = source(file);
            int movement = source.indexOf(
                    "WeaponSkillMovementArbiter.revokeCurrentIfPlayer(target)"
            );
            int velocity = source.indexOf("target.setDeltaMovement(", movement);
            assertTrue(movement >= 0, file);
            assertTrue(velocity > movement, file);
        }
    }

    @Test
    void femurSingleTargetStaggerDoesNotEraseItsOwnKnockback()
            throws IOException {
        String source = source(
                "combat/skill/handler/FemurSlamExecutionState.java"
        );
        String slam = method(
                source,
                "private static void applyControl("
        );
        assertOrdered(
                slam,
                "target.setDeltaMovement(",
                "target.hasImpulse = true",
                "applyKnockback(player, target, knockback)"
        );
    }

    private static void assertOrdered(String source, String... needles) {
        int previous = -1;
        for (String needle : needles) {
            int current = source.indexOf(needle, previous + 1);
            assertTrue(current > previous, "Missing or unordered: " + needle);
            previous = current;
        }
    }

    private static int occurrences(String source, String needle) {
        int count = 0;
        int index = source.indexOf(needle);
        while (index >= 0) {
            count++;
            index = source.indexOf(needle, index + needle.length());
        }
        return count;
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

    private static String source(String relativeFile) throws IOException {
        return Files.readString(findMainJavaRoot().resolve(Path.of(
                "com",
                "stardew",
                "craft"
        )).resolve(relativeFile));
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

    private record TeleportHelper(String file, String signature) {
    }

    private static final class SourceReadException extends RuntimeException {
        private final IOException ioException;

        private SourceReadException(IOException ioException) {
            super(ioException);
            this.ioException = ioException;
        }
    }
}
