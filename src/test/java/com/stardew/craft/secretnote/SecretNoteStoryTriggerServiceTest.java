package com.stardew.craft.secretnote;

import com.stardew.craft.player.PlayerStardewData;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecretNoteStoryTriggerServiceTest {
    @Test
    void qiCaveDialogueBoundaryMatchesOriginal() {
        assertTrue(SecretNoteStoryTriggerService.isHonorableQiCaveRun(0));
        assertTrue(SecretNoteStoryTriggerService.isHonorableQiCaveRun(10));
        assertFalse(SecretNoteStoryTriggerService.isHonorableQiCaveRun(11));
    }

    @Test
    void temporaryRewardRequiresNoteQuestExactFloorAndUnclaimedState() {
        PlayerStardewData data = new PlayerStardewData(UUID.randomUUID());
        int floor100 = SecretNoteStoryTriggerService.SKULL_CAVERN_FLOOR_100;

        assertFalse(SecretNoteStoryTriggerService.canGrantTemporaryReward(data, true, floor100));
        data.markSecretNoteSeen("stardewcraft:10");
        assertFalse(SecretNoteStoryTriggerService.canGrantTemporaryReward(data, false, floor100));
        assertFalse(SecretNoteStoryTriggerService.canGrantTemporaryReward(data, true, floor100 - 1));
        assertTrue(SecretNoteStoryTriggerService.canGrantTemporaryReward(data, true, floor100));

        data.addMailFlag(SecretNoteStoryFlags.QI_CAVE_TEMP_REWARD_GRANTED);
        assertFalse(SecretNoteStoryTriggerService.canGrantTemporaryReward(data, true, floor100));
    }
}
