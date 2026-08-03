package com.stardew.craft.festival.desert;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DesertFestivalNpcVisitServiceTest {
    @Test
    void linusAndGeorgeVisitorOverridesKeepTheirVanillaScheduleDialogue() {
        assertEquals("dialogue:stardewcraft.npc.schedule.linus.desert_festival",
                DesertFestivalNpcVisitService.daySpecificVisit(2, "linus").behavior());
        assertEquals("dialogue:stardewcraft.npc.schedule.george.desert_festival",
                DesertFestivalNpcVisitService.daySpecificVisit(3, "george").behavior());
    }
}
