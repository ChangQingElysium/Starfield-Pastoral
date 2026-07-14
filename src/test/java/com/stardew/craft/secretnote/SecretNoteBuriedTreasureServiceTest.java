package com.stardew.craft.secretnote;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SecretNoteBuriedTreasureServiceTest {
    @Test
    void userCapturedRangesMapToTheVanillaRewards() {
        assertTreasure(16, "SecretNote16_done", new BlockPos(-35, 84, -210));
        assertTreasure(16, "SecretNote16_done", new BlockPos(-25, 84, -204));
        assertTreasure(17, "SecretNote17_done", new BlockPos(101, 63, -74));
        assertTreasure(17, "SecretNote17_done", new BlockPos(103, 63, -70));
        assertTreasure(18, "SecretNote18_done", new BlockPos(-192, 63, -147));
        assertTreasure(18, "SecretNote18_done", new BlockPos(-178, 63, -140));
    }

    @Test
    void positionsOutsideTheCapturedRangesAreNotSpecialDigTargets() {
        assertNull(SecretNoteBuriedTreasureService.treasureAt(new BlockPos(-36, 84, -210)));
        assertNull(SecretNoteBuriedTreasureService.treasureAt(new BlockPos(101, 64, -74)));
        assertNull(SecretNoteBuriedTreasureService.treasureAt(new BlockPos(-177, 63, -140)));
    }

    private static void assertTreasure(int number, String flag, BlockPos pos) {
        var treasure = SecretNoteBuriedTreasureService.treasureAt(pos);
        assertEquals(number, treasure.vanillaNumber());
        assertEquals(flag, treasure.completionFlag());
    }
}
