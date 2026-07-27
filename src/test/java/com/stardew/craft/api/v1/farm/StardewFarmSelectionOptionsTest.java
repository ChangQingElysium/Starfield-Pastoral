package com.stardew.craft.api.v1.farm;

import com.stardew.craft.api.v1.internal.farm.StardewFarmSelectionOptionRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StardewFarmSelectionOptionsTest {
    private static final AtomicInteger IDS = new AtomicInteger();

    @Test
    void optionsAreStableAndSubmissionFailuresAreIsolated() {
        int suffix = IDS.incrementAndGet();
        ResourceLocation firstId = id("first_" + suffix);
        ResourceLocation secondId = id("second_" + suffix);
        List<String> calls = new ArrayList<>();

        StardewFarmSelectionOptions.register(
                secondId, 100, Component.literal("Second"), Component.empty(), false,
                selection -> calls.add("second:" + selection.selected()));
        StardewFarmSelectionOptions.register(
                firstId, 200, Component.literal("First"), Component.empty(), true,
                selection -> {
                    calls.add("first:" + selection.selected());
                    throw new IllegalStateException("expected test failure");
                });

        List<StardewFarmSelectionOptions.Option> testOptions =
                StardewFarmSelectionOptionRegistry.options().stream()
                        .filter(option -> option.id().equals(firstId) || option.id().equals(secondId))
                        .toList();
        assertEquals(List.of(firstId, secondId),
                testOptions.stream().map(StardewFarmSelectionOptions.Option::id).toList());

        for (StardewFarmSelectionOptions.Option option : testOptions) {
            StardewFarmSelectionOptionRegistry.dispatch(
                    option, option.defaultSelected(), "standard", "Test Farm", false);
        }
        assertEquals(List.of("first:true", "second:false"), calls);
    }

    @Test
    void duplicateOptionIdsAreRejected() {
        ResourceLocation duplicateId = id("duplicate_" + IDS.incrementAndGet());
        StardewFarmSelectionOptions.register(
                duplicateId, 0, Component.literal("One"), Component.empty(), false,
                selection -> {
                });
        assertThrows(IllegalStateException.class, () -> StardewFarmSelectionOptions.register(
                duplicateId, 0, Component.literal("Two"), Component.empty(), false,
                selection -> {
                }));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("farm_selection_test", path);
    }
}
