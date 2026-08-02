package com.stardew.craft.combat.skill.handler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeObservedExecutionStateContractTest {
    private static final List<String> REMOVED_TRACKERS = List.of(
            "DarkSwordBloodDebtTracker",
            "DragontoothShivBreathTracker",
            "IridiumNeedleFrenzyTracker",
            "LightCounterParryState",
            "InsectEyeStanceTracker",
            "ElfBladeTracker",
            "OssifiedExecutionTracker",
            "TemperedQuenchTracker",
            "SteelSpineFuryState",
            "TemplarJudgementTracker",
            "TemplarVowTracker",
            "DwarfDaggerRushTracker"
    );

    @Test
    void combatEventsReadOnlyExactRuntimeExecutionStates()
            throws IOException {
        String events = source("combat/WeaponCombatEvents.java");
        String preparation = source("combat/WeaponHitPreparation.java");
        String assembly = source("combat/WeaponDamageAssemblyRules.java");
        String evaluatedSkills = source(
                "combat/BuiltinSkillEvaluatedHitRules.java"
        );
        String appliedSkills = source(
                "combat/BuiltinSkillAppliedHitRules.java"
        );
        String appliedPassives = source(
                "combat/BuiltinWeaponPassiveAppliedHitRules.java"
        );
        String compactAppliedPassives = appliedPassives.replaceAll(
                "\\s+",
                ""
        );
        String coordinator = source(
                "combat/WeaponEvaluatedHitCoordinator.java"
        );
        String calculator = source("combat/DamageCalculator.java");

        assertTrue(appliedSkills.contains(
                "DarkSwordBloodDebtSkillHandler.getLifestealRatio("
        ));
        assertTrue(preparation.contains(
                "DragontoothShivBreathSkillHandler.isActive("
        ));
        assertTrue(preparation.contains(
                "IridiumNeedleFrenzySkillHandler.isActive("
        ));
        assertTrue(preparation.contains(
                "IridiumNeedleFrenzySkillHandler.CRIT_CHANCE_BONUS"
        ));
        assertTrue(compactAppliedPassives.contains(
                "IridiumNeedleFrenzySkillHandler.CRITICAL_HEAL_AMOUNT"
        ));
        assertTrue(compactAppliedPassives.contains(
                "IridiumNeedleFrenzySkillHandler."
                        + "CRITICAL_ENERGY_RESTORE"
        ));
        assertTrue(compactAppliedPassives.contains(
                "IridiumNeedleFrenzySkillHandler."
                        + "CRITICAL_VULNERABLE_DURATION_TICKS"
        ));
        assertTrue(compactAppliedPassives.contains(
                "IridiumNeedleFrenzySkillHandler."
                        + "CRITICAL_VULNERABLE_AMPLIFIER"
        ));
        assertTrue(calculator.contains(
                "DragontoothShivBreathSkillHandler.isActive("
        ));
        assertTrue(preparation.contains(
                "InsectEyeStanceSkillHandler.consumeAttack("
        ));
        assertTrue(appliedSkills.contains(
                "ElfBladeLeafSkillHandler.fireLeafAtTarget("
        ));
        assertTrue(appliedSkills.contains(
                "TemperedQuenchSkillHandler.armBlast("
        ));
        assertTrue(preparation.contains(
                "SteelSpineFurySkillHandler.consumeAttack("
        ));
        assertTrue(calculator.contains(
                "OssifiedExecutionSkillHandler.getCritDamageBonus("
        ));
        int steel = coordinator.indexOf(
                "BuiltinSkillEvaluatedHitRules.emitSteelSpineStrike(hit)"
        );
        int bind = coordinator.indexOf(
                "CommonWeaponEvaluatedHitRules.bindAppliedHitFrame(hit)"
        );
        assertTrue(steel >= 0);
        assertTrue(bind > steel);
        assertFalse(coordinator.contains(
                "BuiltinWeaponPassiveEvaluatedHitRules"
        ));

        String appliedCoordinator = source(
                "combat/WeaponAppliedHitCoordinator.java"
        );
        int quench = appliedCoordinator.indexOf(
                "BuiltinSkillAppliedHitRules.armTemperedQuench(hit)"
        );
        int lifesteal = appliedCoordinator.indexOf(
                "BuiltinSkillAppliedHitRules.applyDarkSwordLifeSteal(hit)"
        );
        int iridium = appliedCoordinator.indexOf(
                "BuiltinWeaponPassiveAppliedHitRules.applyIridiumNeedle(hit)"
        );
        int leaf = appliedCoordinator.indexOf(
                "BuiltinSkillAppliedHitRules.fireElfLeaf(hit)"
        );
        int resources = appliedCoordinator.indexOf(
                "BuiltinWeaponPassiveAppliedHitRules."
                        + "addAppliedWeaponResources(hit)"
        );
        int killRewards = appliedCoordinator.indexOf(
                "CommonWeaponAppliedHitRules.applyKillRewards(hit)"
        );
        int obsidian = appliedCoordinator.indexOf(
                "BuiltinWeaponPassiveAppliedHitRules."
                        + "consumeObsidianResonance(hit)"
        );
        int crystal = appliedCoordinator.indexOf(
                "BuiltinWeaponPassiveAppliedHitRules.triggerCrystalBurst(hit)"
        );
        int infinity = appliedCoordinator.indexOf(
                "triggerEvolvedSingularityFollowup(hit)"
        );
        assertTrue(quench >= 0 && lifesteal > quench);
        assertTrue(iridium > lifesteal);
        assertTrue(leaf > iridium);
        assertTrue(resources > leaf);
        assertTrue(killRewards > resources);
        assertTrue(obsidian > killRewards);
        assertTrue(crystal > obsidian);
        assertTrue(infinity > crystal);

        String stageOwners = events
                + preparation
                + assembly
                + evaluatedSkills
                + appliedSkills
                + appliedPassives
                + coordinator;
        for (String tracker : REMOVED_TRACKERS) {
            assertFalse(stageOwners.contains(tracker), tracker);
            assertFalse(calculator.contains(tracker), tracker);
        }
    }

    @Test
    void eachFacadePinsCasterSkillAndConcreteStateType()
            throws IOException {
        assertTypedFacade(
                "DarkSwordBloodDebtSkillHandler.java",
                "BuiltinWeaponSkillHandlers.DARK_SWORD_BLOOD_DEBT",
                "DarkSwordBloodDebtExecutionState.class"
        );
        assertTypedFacade(
                "DragontoothShivBreathSkillHandler.java",
                "BuiltinWeaponSkillHandlers.DRAGONTOOTH_SHIV_BREATH",
                "DragontoothShivBreathExecutionState.class"
        );
        assertTypedFacade(
                "IridiumNeedleFrenzySkillHandler.java",
                "BuiltinWeaponSkillHandlers.IRIDIUM_NEEDLE_FRENZY",
                "IridiumNeedleFrenzyExecutionState.class"
        );
        assertTypedFacade(
                "LightCounterSkillHandler.java",
                "BuiltinWeaponSkillHandlers.LIGHT_COUNTER",
                "LightCounterExecutionState.class"
        );
        assertTypedFacade(
                "InsectEyeStanceSkillHandler.java",
                "BuiltinWeaponSkillHandlers.INSECT_EYE_STANCE",
                "InsectEyeStanceExecutionState.class"
        );
        assertTypedFacade(
                "ElfBladeLeafSkillHandler.java",
                "BuiltinWeaponSkillHandlers.ELF_BLADE_LEAF",
                "ElfBladeLeafExecutionState.class"
        );
        assertTypedFacade(
                "OssifiedExecutionSkillHandler.java",
                "BuiltinWeaponSkillHandlers.OSSIFIED_EXECUTION",
                "OssifiedExecutionState.class"
        );
        assertTypedFacade(
                "TemperedQuenchSkillHandler.java",
                "BuiltinWeaponSkillHandlers.TEMPERED_QUENCH",
                "TemperedQuenchExecutionState.class"
        );
        assertTypedFacade(
                "SteelSpineFurySkillHandler.java",
                "BuiltinWeaponSkillHandlers.STEEL_SPINE_FURY",
                "SteelSpineFuryExecutionState.class"
        );
        assertTypedFacade(
                "TemplarJudgementSkillHandler.java",
                "BuiltinWeaponSkillHandlers.TEMPLAR_JUDGEMENT",
                "TemplarJudgementExecutionState.class"
        );
        assertTypedFacade(
                "TemplarVowSkillHandler.java",
                "BuiltinWeaponSkillHandlers.TEMPLAR_VOW",
                "TemplarVowExecutionState.class"
        );
        assertTypedFacade(
                "DwarfDaggerRushSkillHandler.java",
                "BuiltinWeaponSkillHandlers.DWARF_DAGGER_RUSH",
                "DwarfDaggerRushExecutionState.class"
        );
    }

    @Test
    void centralizedCleanupDoesNotMaintainRemovedParallelMaps()
            throws IOException {
        String cleanup = source("combat/CombatTrackerCleanup.java");
        for (String tracker : REMOVED_TRACKERS) {
            assertFalse(cleanup.contains(tracker), tracker);
        }
        assertFalse(cleanup.contains("HolyBladeSanctuaryTracker"));
    }

    private static void assertTypedFacade(
            String handlerFile,
            String skillId,
            String stateType
    ) throws IOException {
        String handler = source(
                "combat/skill/handler/" + handlerFile
        );
        assertTrue(handler.contains(
                "WeaponSkillRuntime.activeExecutionState("
        ), handlerFile);
        assertTrue(handler.contains(".getUUID()"), handlerFile);
        assertTrue(handler.contains(skillId), handlerFile);
        assertTrue(handler.contains(stateType), handlerFile);
    }

    private static String source(String relative) throws IOException {
        return Files.readString(javaRoot().resolve(relative));
    }

    private static Path javaRoot() throws IOException {
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
        throw new IOException("Cannot locate Java source root");
    }
}
