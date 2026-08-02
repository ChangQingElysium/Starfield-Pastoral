package com.stardew.craft.combat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeaponDamageSourceEligibilityTest {
    @Test
    void onlyVanillaPlayerAttackAndExplicitProjectSkillAreEligible() {
        assertTrue(WeaponCombatEvents.isEligibleWeaponDamageSource(
                WeaponCombatEvents.WeaponDamageProvenance.PLAYER_ATTACK
        ));
        assertTrue(WeaponCombatEvents.isEligibleWeaponDamageSource(
                WeaponCombatEvents.WeaponDamageProvenance.PROJECT_SKILL
        ));

        assertFalse(WeaponCombatEvents.isEligibleWeaponDamageSource(
                WeaponCombatEvents.WeaponDamageProvenance
                        .UNAUTHORED_PLAYER_ATTACK
        ));
        assertFalse(WeaponCombatEvents.isEligibleWeaponDamageSource(
                WeaponCombatEvents.WeaponDamageProvenance.THORNS
        ));
        assertFalse(WeaponCombatEvents.isEligibleWeaponDamageSource(
                WeaponCombatEvents.WeaponDamageProvenance.PROJECTILE
        ));
        assertFalse(WeaponCombatEvents.isEligibleWeaponDamageSource(
                WeaponCombatEvents.WeaponDamageProvenance.EXPLOSION
        ));
        assertFalse(WeaponCombatEvents.isEligibleWeaponDamageSource(
                WeaponCombatEvents.WeaponDamageProvenance.OTHER
        ));
    }

    @Test
    void rejectedDamageCannotConsumePendingWeaponSkillContext()
            throws IOException {
        String source = readCombatSource("WeaponCombatEvents.java");
        int customEntry = source.indexOf(
                "public static CustomHealthWeaponResolution "
                        + "evaluateCustomHealthWeaponHit("
        );
        int classify = source.indexOf(
                "classifyWeaponDamageProvenance(",
                customEntry
        );
        int eligibilityGate = source.indexOf(
                "if (!isEligibleWeaponDamageSource("
                        + "admission.provenance())) {",
                classify
        );
        int evaluate = source.indexOf(
                "IncomingWeaponResolution resolution = evaluateWeaponHit(",
                eligibilityGate
        );

        assertTrue(customEntry >= 0);
        assertTrue(classify > customEntry);
        assertTrue(eligibilityGate > classify);
        assertTrue(evaluate > eligibilityGate);

        int evaluator = source.indexOf(
                "private static IncomingWeaponResolution evaluateWeaponHit("
        );
        int consume = source.indexOf(
                "WeaponSkillContextStore.consumePending(",
                evaluator
        );
        assertTrue(evaluator >= 0);
        assertTrue(consume > evaluator);

        int classifier = source.indexOf(
                "private static WeaponDamageAdmission "
                        + "classifyWeaponDamageProvenance("
        );
        int classifierEnd = source.indexOf(
                "\n    }\n\n",
                classifier
        );
        assertTrue(classifier >= 0);
        assertTrue(classifierEnd > classifier);
        String classifierBody = source.substring(classifier, classifierEnd);

        assertTrue(classifierBody.contains(
                "source.is(DamageTypes.PLAYER_ATTACK)"
        ));
        assertTrue(classifierBody.contains(
                "source.is(DamageTypes.THORNS)"
        ));
        assertTrue(classifierBody.contains(
                "source.is(DamageTypeTags.IS_PROJECTILE)"
        ));
        assertTrue(classifierBody.contains(
                "instanceof MeowmereProjectileEntity"
        ));
        assertTrue(classifierBody.contains(
                "WeaponSkillContextStore.hasPending(player, nowTick)"
        ));
        assertTrue(classifierBody.contains(
                "OrdinaryWeaponAttackFrameStore.claim("
        ));
        assertTrue(classifierBody.contains(
                "WeaponDamageProvenance.UNAUTHORED_PLAYER_ATTACK"
        ));
        assertTrue(classifierBody.contains(
                "source.is(DamageTypeTags.IS_EXPLOSION)"
        ));
    }

    @Test
    void meowmereProjectileRequiresItsReleaseSnapshotForDamage()
            throws IOException {
        String source = readSource(Path.of(
                "src", "main", "java", "com", "stardew", "craft",
                "entity", "projectile", "MeowmereProjectileEntity.java"
        ));
        int hit = source.indexOf("protected void onHitEntity(");
        int blockHit = source.indexOf("protected void onHitBlock(", hit);
        String method = source.substring(hit, blockHit);

        assertTrue(method.contains("releaseWeaponSnapshot == null"));
        assertTrue(method.contains("WeaponSkillContextStore.setPending("));
        assertTrue(method.contains("releaseWeaponSnapshot,"));
        assertFalse(method.contains(
                "WeaponSkillContextStore.setPending(\n"
                        + "                        skillPlayer,\n"
                        + "                        hitContext,\n"
                        + "                        nowTick"
        ));
    }

    @Test
    void authoredSkillsKeepTheirExplicitPlayerAttackDamageSource()
            throws IOException {
        String source = readSkillSource("WeaponSkillDamage.java");

        assertTrue(source.contains(
                "serverPlayer.damageSources().playerAttack(serverPlayer)"
        ));
    }

    private static String readCombatSource(String fileName) throws IOException {
        return readSource(Path.of(
                "src",
                "main",
                "java",
                "com",
                "stardew",
                "craft",
                "combat",
                fileName
        ));
    }

    private static String readSkillSource(String fileName) throws IOException {
        return readSource(Path.of(
                "src",
                "main",
                "java",
                "com",
                "stardew",
                "craft",
                "combat",
                "skill",
                fileName
        ));
    }

    private static String readSource(Path relative) throws IOException {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relative);
            if (Files.isRegularFile(candidate)) {
                return Files.readString(candidate);
            }
            current = current.getParent();
        }
        throw new IOException("Cannot locate " + relative);
    }
}
