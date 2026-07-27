package com.stardew.craft.api.v1.requirement;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StardewRequirementTypesTest {
    @Test
    void reportsDefensivelyCopyAndExposeBlockingRows() {
        StardewRequirement satisfied = new StardewRequirement(
                ResourceLocation.parse("example:open"),
                StardewRequirement.State.SATISFIED,
                Component.literal("Open"),
                true);
        StardewRequirement blocked = new StardewRequirement(
                ResourceLocation.parse("example:level"),
                StardewRequirement.State.UNSATISFIED,
                Component.literal("Level 5"),
                true);
        ArrayList<StardewRequirement> source =
                new ArrayList<>(List.of(satisfied, blocked));
        StardewRequirementReport report =
                new StardewRequirementReport(source);

        source.clear();
        assertFalse(report.satisfied());
        assertEquals(List.of(blocked), report.blocking());
        assertThrows(
                UnsupportedOperationException.class,
                () -> report.requirements().clear());
    }
}
