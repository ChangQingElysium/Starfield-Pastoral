package com.stardew.craft.blockentity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AutoGrabberStorageContractTest {
    @Test
    void exposesTheSourceThirtySixSlotStorageAsFourRows() {
        assertEquals(36, AutoGrabberBlockEntity.SLOT_COUNT);
        assertEquals(
                AutoGrabberBlockEntity.SLOT_COUNT,
                AutoGrabberBlockEntity.STORAGE_ROWS * 9
        );
    }
}
