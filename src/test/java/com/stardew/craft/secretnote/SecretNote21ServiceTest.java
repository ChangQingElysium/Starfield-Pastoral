package com.stardew.craft.secretnote;

import com.stardew.craft.player.PlayerStardewData;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecretNote21ServiceTest {
    @Test
    void requiresReadNoteExactTwelveFortyAndUnseenEvent() {
        PlayerStardewData data = new PlayerStardewData(UUID.randomUUID());

        assertFalse(SecretNote21Service.canTrigger(data, SecretNote21Service.TRIGGER_TIME));
        data.markSecretNoteSeen(SecretNote21Service.NOTE_ID);
        assertFalse(SecretNote21Service.canTrigger(data, SecretNote21Service.TRIGGER_TIME - 10));
        assertFalse(SecretNote21Service.canTrigger(data, SecretNote21Service.TRIGGER_TIME + 10));
        assertTrue(SecretNote21Service.canTrigger(data, SecretNote21Service.TRIGGER_TIME));

        data.addMailFlag(SecretNote21Service.DONE_FLAG);
        assertFalse(SecretNote21Service.canTrigger(data, SecretNote21Service.TRIGGER_TIME));
    }

    @Test
    void targetBushUsesAuthoredThreeByTwoByThreeRange() {
        assertTrue(SecretNote21Service.isTargetBush(new net.minecraft.core.BlockPos(34, 64, 51)));
        assertTrue(SecretNote21Service.isTargetBush(new net.minecraft.core.BlockPos(36, 65, 53)));
        assertTrue(SecretNote21Service.isTargetBush(SecretNote21Service.ACTOR_ORIGIN));
        assertFalse(SecretNote21Service.isTargetBush(new net.minecraft.core.BlockPos(33, 64, 52)));
        assertFalse(SecretNote21Service.isTargetBush(new net.minecraft.core.BlockPos(35, 66, 52)));
        assertFalse(SecretNote21Service.isTargetBush(new net.minecraft.core.BlockPos(35, 64, 54)));
    }
}
