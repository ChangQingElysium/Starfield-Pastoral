package com.stardew.craft.item.misc;

import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.secretnote.SecretNoteStoryFlags;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IridiumMilkItemTest {
    @Test
    void permanentRewardCanOnlyBeAppliedOnce() {
        PlayerStardewData data = new PlayerStardewData(UUID.randomUUID());
        assertTrue(IridiumMilkItem.canApplyPermanentReward(data));

        data.addMailFlag(SecretNoteStoryFlags.QI_CAVE);

        assertFalse(IridiumMilkItem.canApplyPermanentReward(data));
    }

    @Test
    void legacyConsumptionFlagAlsoPreventsDuplicateHealthReward() {
        PlayerStardewData data = new PlayerStardewData(UUID.randomUUID());
        data.addMailFlag(SecretNoteStoryFlags.IRIDIUM_MILK_CONSUMED);

        assertFalse(IridiumMilkItem.canApplyPermanentReward(data));
    }
}
