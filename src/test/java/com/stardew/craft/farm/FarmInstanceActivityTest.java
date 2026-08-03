package com.stardew.craft.farm;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FarmInstanceActivityTest {
    @Test
    void visitActivityPersistsAndNeverMovesBackwards() {
        FarmInstance farm = new FarmInstance(
                UUID.randomUUID(), "Owner", "Farm", 0,
                BlockPos.ZERO, FarmType.STANDARD);

        farm.markActiveOnDay(7);
        farm.markActiveOnDay(5);

        FarmInstance loaded = FarmInstance.load(farm.save());
        assertEquals(7, loaded.getLastActiveDay());
        assertTrue(loaded.wasActiveOnDay(7));
        assertFalse(loaded.wasActiveOnDay(8));
    }

    @Test
    void oldSaveFallsBackToLastSettledDay() {
        FarmInstance farm = new FarmInstance(
                UUID.randomUUID(), "Owner", "Farm", 0,
                BlockPos.ZERO, FarmType.STANDARD);
        farm.setLastOnlineDay(12);
        CompoundTag legacy = farm.save();
        legacy.remove("LastActiveDay");

        assertEquals(12, FarmInstance.load(legacy).getLastActiveDay());
    }

    @Test
    void guestVisitSettlesFarmWhileOwnerIsOffline() {
        UUID owner = UUID.randomUUID();
        FarmInstance farm = new FarmInstance(
                owner, "Owner", "Farm", 0,
                BlockPos.ZERO, FarmType.STANDARD);
        farm.markActiveOnDay(9);

        assertTrue(FarmDailyProcessHelper.shouldSettleFarm(farm, Set.of(), 9));
        assertFalse(FarmDailyProcessHelper.shouldSettleFarm(farm, Set.of(), 10));
        assertTrue(FarmDailyProcessHelper.shouldSettleFarm(farm, Set.of(owner), 10));
    }
}
