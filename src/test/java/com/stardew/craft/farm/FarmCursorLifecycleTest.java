package com.stardew.craft.farm;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class FarmCursorLifecycleTest {
    @Test
    void newFarmStartsAtCreationDateAndExistingFarmKeepsItsCursor() {
        FarmInstanceRegistry registry = new FarmInstanceRegistry();
        UUID owner = UUID.randomUUID();

        FarmInstance created = registry.createFarmAtDate(
                owner, "Leah", "Forest", FarmType.STANDARD, 87, 3);
        FarmInstance repeated = registry.createFarmAtDate(
                owner, "Leah", "Forest", FarmType.STANDARD, 99, 0);

        assertSame(created, repeated);
        assertEquals(87, created.getLastOnlineDay());
        assertEquals(3, created.getLastOnlineSeason());
    }
}
