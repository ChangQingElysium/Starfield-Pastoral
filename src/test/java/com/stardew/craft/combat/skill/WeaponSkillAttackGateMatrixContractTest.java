package com.stardew.craft.combat.skill;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeaponSkillAttackGateMatrixContractTest {
    private static final List<String> DIRECT_ATTACKS = List.of(
            "combat/skill/LightCounterParryHandler.java",
            "combat/skill/SilverSaberSkillHelper.java",
            "combat/skill/TemplarVowHandler.java",
            "combat/skill/handler/BoneFractureSkillHandler.java",
            "combat/skill/handler/BurglarShankSkillHandler.java",
            "combat/skill/handler/CarvingThrustExecutionState.java",
            "combat/skill/handler/ClaymoreFoldbackExecutionState.java",
            "combat/skill/handler/ClaymoreFoldbackSkillHandler.java",
            "combat/skill/handler/CrescentSlashSkillHandler.java",
            "combat/skill/handler/CrystalDaggerLayerSkillHandler.java",
            "combat/skill/handler/DarkSwordBloodDebtSkillHandler.java",
            "combat/skill/handler/DesperatePlunderSkillHandler.java",
            "combat/skill/handler/DragonBreathJudgementSkillHandler.java",
            "combat/skill/handler/DragonBreathThrustSkillHandler.java",
            "combat/skill/handler/DragontoothShivStabSkillHandler.java",
            "combat/skill/handler/DwarfDaggerThrustExecutionState.java",
            "combat/skill/handler/DwarfRuneGuardSkillHandler.java",
            "combat/skill/handler/FemurSlamExecutionState.java",
            "combat/skill/handler/FishcatchThrustExecutionState.java",
            "combat/skill/handler/ForestBlessingSkillHandler.java",
            "combat/skill/handler/GalaxyDaggerStarleapSkillHandler.java",
            "combat/skill/handler/GalaxyDaggerThrustExecutionState.java",
            "combat/skill/handler/GalaxyJudgementSkillHandler.java",
            "combat/skill/handler/HolySmiteSkillHandler.java",
            "combat/skill/handler/InfinityDaggerSingularityBackstabSkillHandler.java",
            "combat/skill/handler/InfinityDaggerThrustExecutionState.java",
            "combat/skill/handler/InsectDashSkillHandler.java",
            "combat/skill/handler/IridiumNeedleThrustExecutionState.java",
            "combat/skill/handler/IronDirkThrustSkillHandler.java",
            "combat/skill/handler/LavaKatanaBrandSkillHandler.java",
            "combat/skill/handler/StartrailRiftSkillHandler.java",
            "combat/skill/handler/TemperedQuenchSkillHandler.java",
            "combat/skill/handler/TemplarJudgementExecutionState.java",
            "combat/skill/handler/TemplarVowExecutionState.java",
            "combat/skill/handler/TetanusStrikeSkillHandler.java",
            "combat/skill/handler/TideReelSkillHandler.java",
            "combat/skill/handler/TreeBlessingSkillHandler.java",
            "combat/skill/handler/WindSpireThrustSkillHandler.java",
            "combat/skill/handler/YetiToothMarkSkillHandler.java"
    );

    private static final List<String> SKILL_EFFECTS = List.of(
            "combat/skill/LavaKatanaMarkTracker.java",
            "combat/skill/RiftPathDamageTracker.java",
            "combat/skill/TemperedFireRingTracker.java",
            "combat/skill/handler/DarkSwordBloodMoonExecutionState.java",
            "combat/skill/handler/DwarfFortressExecutionState.java",
            "combat/skill/handler/EternalCollapseExecutionState.java",
            "combat/skill/handler/GalaxyJudgementExecutionState.java",
            "combat/skill/handler/HolyDomainExecutionState.java",
            "combat/skill/handler/LavaKatanaReverbExecutionState.java",
            "combat/skill/handler/ObsidianCrackExecutionState.java",
            "combat/skill/handler/OssifiedExecutionState.java",
            "combat/skill/handler/SteelFalchionDotTracker.java",
            "combat/skill/handler/SteelFalchionTraceExecutionState.java",
            "combat/skill/handler/TemperedQuenchExecutionState.java"
    );

    @Test
    void independentWeaponAttacksExplicitlyRespectTheImpactGate()
            throws IOException {
        for (String relative : DIRECT_ATTACKS) {
            String source = readSource(relative);
            assertTrue(
                    source.contains("WeaponSkillDamage.apply"),
                    relative
            );
            assertTrue(
                    source.contains(".RESPECT_AT_IMPACT"),
                    relative
            );
        }
    }

    @Test
    void fieldsDotsAndExplosionsExplicitlyRemainSkillDamage()
            throws IOException {
        for (String relative : SKILL_EFFECTS) {
            String source = readSource(relative);
            assertTrue(
                    source.contains("WeaponSkillDamage.apply"),
                    relative
            );
            assertTrue(source.contains(".SKILL_DAMAGE"), relative);
            assertFalse(source.contains(".RESPECT_AT_IMPACT"), relative);
        }
    }

    @Test
    void singularitySeparatesExplosionFromThePhysicalDashSlash()
            throws IOException {
        String source = readSource(
                "combat/skill/handler/SingularityEvolveExecutionState.java"
        );
        int explosion = source.indexOf(".EXPLOSION_DAMAGE_MULTIPLIER");
        int explosionPolicy = source.indexOf(".SKILL_DAMAGE", explosion);
        int slash = source.indexOf(".SLASH_DAMAGE_MULTIPLIER");
        int slashPolicy = source.indexOf(".RESPECT_AT_IMPACT", slash);
        int helper = source.indexOf("private void applyDamage(");

        assertTrue(explosion >= 0);
        assertTrue(explosionPolicy > explosion && explosionPolicy < slash);
        assertTrue(slash > explosionPolicy);
        assertTrue(slashPolicy > slash && slashPolicy < helper);
        assertTrue(helper > slashPolicy);
        assertTrue(source.contains(
                "WeaponSkillDamage.AttackGatePolicy attackGatePolicy"
        ));
    }

    private static String readSource(String relativeFile)
            throws IOException {
        Path relative = Path.of("src", "main", "java", "com", "stardew", "craft")
                .resolve(relativeFile);
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
