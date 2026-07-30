package com.stardew.craft.item.tool;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PointPlanWandItemTest {
    @Test
    void npcIdsStayNormalizedWhileMapPointNamesRemainHumanReadable() {
        assertEquals(
                "abigail_display",
                PointPlanWandItem.normalizeEntryName(
                        PointPlanWandItem.EditorKind.NPC,
                        "Abigail Display"));

        assertEquals(
                "刘易斯的冰箱",
                PointPlanWandItem.normalizeEntryName(
                        PointPlanWandItem.EditorKind.MAP_INTERACTION,
                        " 刘易斯的冰箱 "));
    }
}
