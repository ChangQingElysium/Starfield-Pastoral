package com.stardew.craft.npc.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcDialogueTopicServiceTest {
    @Test
    void greenRainFinishedOnlyActivatesAfterYearOneGreenRain() {
        assertTrue(NpcDialogueTopicService.isYearOneGreenRain("GreenRain", 1));
        assertTrue(NpcDialogueTopicService.isYearOneGreenRain("green_rain", 1));
        assertFalse(NpcDialogueTopicService.isYearOneGreenRain("Rain", 1));
        assertFalse(NpcDialogueTopicService.isYearOneGreenRain("GreenRain", 2));
    }

    @Test
    void mineFloorsMapToVanillaMineAreaTopics() {
        assertEquals("", NpcDialogueTopicService.mineAreaTopic(39));
        assertEquals("mineArea_40", NpcDialogueTopicService.mineAreaTopic(40));
        assertEquals("mineArea_40", NpcDialogueTopicService.mineAreaTopic(79));
        assertEquals("mineArea_80", NpcDialogueTopicService.mineAreaTopic(80));
        assertEquals("mineArea_80", NpcDialogueTopicService.mineAreaTopic(120));
        assertEquals("mineArea_121", NpcDialogueTopicService.mineAreaTopic(121));
    }
}
