package com.stardew.craft.combat.equipment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomNegativeStatusProtectionContractTest {
    private static final List<StatusEntry> CUSTOM_STATUSES = List.of(
            new StatusEntry(
                    "combat/skill/LavaKatanaMarkTracker.java",
                    "private static void applyInternal("
            ),
            new StatusEntry(
                    "combat/skill/ElfBladeMarkTracker.java",
                    "public static void apply("
            ),
            new StatusEntry(
                    "combat/skill/GalaxyDaggerMarkTracker.java",
                    "public static void apply("
            ),
            new StatusEntry(
                    "combat/skill/InfinityDaggerMarkTracker.java",
                    "public static void apply("
            ),
            new StatusEntry(
                    "combat/skill/OssifiedMarkTracker.java",
                    "public static void apply("
            ),
            new StatusEntry(
                    "combat/skill/TideMarkTracker.java",
                    "public static void apply("
            ),
            new StatusEntry(
                    "combat/skill/YetiToothMarkTracker.java",
                    "public static int applyWithEquipmentProtection("
            )
    );

    @Test
    void everyCustomHostileStatusDecidesProtectionBeforeWritingState()
            throws IOException {
        for (StatusEntry entry : CUSTOM_STATUSES) {
            String body = method(source(entry.file()), entry.signature());
            assertOrdered(
                    body,
                    "EquipmentNegativeStatusProtection.decide(",
                    "protection.resisted()",
                    "tag.putLong(TAG_END_TICK"
            );
            assertTrue(
                    body.contains("protection.durationTicks()"),
                    entry.file()
            );
        }
    }

    @Test
    void compositeStatusesReuseOneDecisionForEveryComponent()
            throws IOException {
        String yeti = source("combat/skill/YetiFreezeTracker.java");
        String yetiProtected = method(
                yeti,
                "public static int applyWithEquipmentProtection("
        );
        assertOrdered(
                yetiProtected,
                "EquipmentNegativeStatusProtection.decide(",
                "protection.resisted()",
                "return applyPreAdjusted("
        );
        String yetiPreAdjusted = method(
                yeti,
                "public static int applyPreAdjusted("
        );
        assertTrue(yetiPreAdjusted.contains("tag.putLong(TAG_END_TICK"));
        assertTrue(yetiPreAdjusted.contains("appliedDuration"));

        String bone = method(
                source("combat/BuiltinSkillAppliedHitRules.java"),
                "static void applyBoneFracture("
        );
        assertEquals(
                1,
                occurrences(
                        bone,
                        "EquipmentNegativeStatusProtection.decide("
                )
        );
        assertEquals(
                2,
                occurrences(
                        bone,
                        "EquipmentMobEffectHandler.addPreAdjustedEffect("
                )
        );
        assertOrdered(
                bone,
                "int duration = protection.durationTicks()",
                "BoneFractureTracker.apply("
        );

        String yetiMark = method(
                source("combat/BuiltinSkillAppliedHitRules.java"),
                "static void applyYetiMark("
        );
        assertOrdered(
                yetiMark,
                "int markDuration = "
                        + "YetiToothMarkTracker.applyWithEquipmentProtection(",
                "YetiToothEffects.applyPreAdjustedSlow("
        );

        String anchor = method(
                source("entity/projectile/TideAnchorProjectileEntity.java"),
                "private void handleImpact("
        );
        assertEquals(
                1,
                occurrences(
                        anchor,
                        "EquipmentNegativeStatusProtection.decide("
                )
        );
        assertEquals(
                2,
                occurrences(
                        anchor,
                        "EquipmentMobEffectHandler.addPreAdjustedEffect("
                )
        );
        assertOrdered(
                anchor,
                "int rootDuration = protection.durationTicks()",
                "TideAnchorRootTracker.applyPreAdjusted("
        );

        String poison = method(
                source("combat/skill/WickedKrisPoisonTracker.java"),
                "private static void applyPoisonInternal("
        );
        assertEquals(
                1,
                occurrences(
                        poison,
                        "EquipmentNegativeStatusProtection.decide("
                )
        );
        assertOrdered(
                poison,
                "EquipmentNegativeStatusProtection.decide(",
                "protection.resisted()",
                "int appliedDuration = protection.durationTicks()",
                "PoisonEntry replacement = new PoisonEntry(",
                "nowTick + appliedDuration",
                "protection.adjustRelatedDurationTicks("
        );
        assertTrue(poison.contains(
                "replacement.detonateTick = nowTick + delay;"
        ));
        assertTrue(poison.contains(
                "replacement.detonationSnapshot = weaponSnapshot;"
        ));
    }

    @Test
    void obsidianCrackProtectsSlowWithoutProtectingCollisionPull()
            throws IOException {
        String state = source(
                "combat/skill/handler/ObsidianCrackExecutionState.java"
        );
        String explode = method(state, "private void explode(");
        assertOrdered(
                explode,
                "target.teleportTo(",
                "applySlow(target);",
                "WeaponSkillDamage.apply("
        );
        assertEquals(
                1,
                occurrences(
                        state,
                        "EquipmentNegativeStatusProtection.decide("
                )
        );

        String slow = method(state, "private static void applySlow(");
        assertOrdered(
                slow,
                "EquipmentNegativeStatusProtection.decide(",
                "ObsidianCrackSkillHandler.SLOW_DURATION_TICKS",
                "protection.resisted()",
                "EquipmentMobEffectHandler.addPreAdjustedEffect(",
                "protection.durationTicks()"
        );
        assertTrue(explode.contains(
                "WeaponSkillMovementArbiter.revokeCurrent(targetPlayer)"
        ));
        assertTrue(explode.contains("target.teleportTo("));
        assertFalse(explode.contains(
                "EquipmentNegativeStatusProtection.decide("
        ));
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
        assertTrue(openingBrace > start, "Missing body " + signature);
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

    private record StatusEntry(String file, String signature) {
    }
}
