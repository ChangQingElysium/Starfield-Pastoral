package com.stardew.craft.network.overnight;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OvernightSequencePlannerTest {
    @Test
    void emptyNightUsesSaveMenuWithoutShippingMenu() {
        assertEquals(
            List.of(OvernightSequencePlanner.Stage.SAVE),
            OvernightSequencePlanner.plan(0, false)
        );
    }

    @Test
    void shippedItemsUseShippingMenuAsFinalStage() {
        assertEquals(
            List.of(OvernightSequencePlanner.Stage.SHIPPING),
            OvernightSequencePlanner.plan(0, true)
        );
    }

    @Test
    void levelUpsStayInFrontOfTheFinalShippingOrSaveStage() {
        assertEquals(
            List.of(
                OvernightSequencePlanner.Stage.LEVEL_UP,
                OvernightSequencePlanner.Stage.LEVEL_UP,
                OvernightSequencePlanner.Stage.SHIPPING
            ),
            OvernightSequencePlanner.plan(2, true)
        );
        assertEquals(
            List.of(
                OvernightSequencePlanner.Stage.LEVEL_UP,
                OvernightSequencePlanner.Stage.SAVE
            ),
            OvernightSequencePlanner.plan(1, false)
        );
    }
}
