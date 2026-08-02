package com.stardew.craft.player;

import com.stardew.craft.network.overnight.OvernightSequencePlanner;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerProfessionOvernightTest {
    @Test
    void ordinaryLevelFiveGainOpensBeforeSaveOnANightWithoutShipping() {
        PlayerStardewData data = new PlayerStardewData(UUID.randomUUID());
        data.setSkillExperience(SkillType.FARMING, 1_300);
        assertTrue(data.applyPendingSkillLevelUps().isEmpty());
        assertTrue(data.addExperience(SkillType.FARMING, 850));

        List<PlayerStardewData.SkillLevelUp> levelUps = data.applyPendingSkillLevelUps();

        assertEquals(
                List.of(new PlayerStardewData.SkillLevelUp(SkillType.FARMING, 5)),
                levelUps);
        assertEquals(
                List.of(
                        OvernightSequencePlanner.Stage.LEVEL_UP,
                        OvernightSequencePlanner.Stage.SAVE),
                OvernightSequencePlanner.plan(levelUps.size(), false));
    }

    @Test
    void strandedLevelFiveChoiceIsRecoveredIntoTheOvernightQueue() {
        PlayerStardewData data = new PlayerStardewData(UUID.randomUUID());
        data.setSkillExperience(SkillType.FARMING, 2_150);

        List<PlayerStardewData.SkillLevelUp> levelUps = data.applyPendingSkillLevelUps();

        assertEquals(
                List.of(new PlayerStardewData.SkillLevelUp(SkillType.FARMING, 5)),
                levelUps);
        assertEquals(
                List.of(
                        OvernightSequencePlanner.Stage.LEVEL_UP,
                        OvernightSequencePlanner.Stage.SAVE),
                OvernightSequencePlanner.plan(levelUps.size(), false));
    }

    @Test
    void missedLevelTenPlayerCanChooseFiveAndTenInTheSameNight() {
        PlayerStardewData data = new PlayerStardewData(UUID.randomUUID());
        data.setSkillExperience(SkillType.MINING, 15_000);

        assertEquals(
                List.of(
                        new PlayerStardewData.SkillLevelUp(SkillType.MINING, 5),
                        new PlayerStardewData.SkillLevelUp(SkillType.MINING, 10)),
                data.applyPendingSkillLevelUps());
        assertTrue(data.choosePendingProfession(ProfessionType.MINER));
        assertTrue(data.choosePendingProfession(ProfessionType.BLACKSMITH));
        assertFalse(data.hasPendingProfessionChoices());
    }

    @Test
    void uncertaintyStatueRequeuesBothProfessionChoicesWithoutShipping() {
        PlayerStardewData data = new PlayerStardewData(UUID.randomUUID());
        data.addExperience(SkillType.FARMING, 15_000);
        data.applyPendingSkillLevelUps();
        assertTrue(data.choosePendingProfession(ProfessionType.RANCHER));
        assertTrue(data.choosePendingProfession(ProfessionType.COOPMASTER));

        assertTrue(data.respecProfessionsForSkill(SkillType.FARMING));
        assertFalse(data.hasProfession(ProfessionType.RANCHER));
        assertFalse(data.hasProfession(ProfessionType.COOPMASTER));

        List<PlayerStardewData.SkillLevelUp> levelUps = data.applyPendingSkillLevelUps();
        assertEquals(
                List.of(
                        new PlayerStardewData.SkillLevelUp(SkillType.FARMING, 5),
                        new PlayerStardewData.SkillLevelUp(SkillType.FARMING, 10)),
                levelUps);
        assertEquals(
                List.of(
                        OvernightSequencePlanner.Stage.LEVEL_UP,
                        OvernightSequencePlanner.Stage.LEVEL_UP,
                        OvernightSequencePlanner.Stage.SAVE),
                OvernightSequencePlanner.plan(levelUps.size(), false));
    }
}
