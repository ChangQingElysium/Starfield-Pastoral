package com.stardew.craft.npc.runtime;

import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NpcFriendshipMailServiceTest {

    @Test
    void zeroHeartSelectionCanNeverSendMail() {
        assertNull(NpcFriendshipMailService.selectDailyNpc(
                List.of("linus"),
                ignored -> 249,
                RandomSource.create(1L)));
    }

    @Test
    void tenHeartSelectionAlwaysPassesTheVanillaChanceRoll() {
        assertEquals("linus",
                NpcFriendshipMailService.selectDailyNpc(
                        List.of("LiNuS"),
                        ignored -> 2500,
                        RandomSource.create(1L)));
    }
}
