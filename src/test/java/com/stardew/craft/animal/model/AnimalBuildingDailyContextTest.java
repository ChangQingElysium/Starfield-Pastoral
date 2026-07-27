package com.stardew.craft.animal.model;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimalBuildingDailyContextTest {
    @Test
    void contextCopiesDailyMembershipAndDeviceFacts() {
        ArrayList<Long> animals =
                new ArrayList<>(List.of(1L, 2L));
        ArrayList<BlockPos> grabbers =
                new ArrayList<>(List.of(BlockPos.ZERO));
        AnimalBuildingDailyContext context =
                new AnimalBuildingDailyContext(
                        "coop_1",
                        4L,
                        80,
                        new AnimalBuildingCapabilities(
                                "coop",
                                3,
                                12,
                                0,
                                false,
                                true),
                        animals,
                        false,
                        true,
                        true,
                        false,
                        grabbers,
                        List.of(),
                        List.of(new BlockPos(1, 2, 3)),
                        5);

        animals.clear();
        grabbers.clear();

        assertEquals(List.of(1L, 2L), context.animalIds());
        assertEquals(List.of(BlockPos.ZERO), context.autoGrabbers());
        assertTrue(context.capabilities().automaticFeed());
        assertEquals(5, context.pendingProduceCount());
        assertThrows(
                UnsupportedOperationException.class,
                () -> context.animalIds().add(3L));
    }
}
