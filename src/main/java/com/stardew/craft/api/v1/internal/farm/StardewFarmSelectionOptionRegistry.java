package com.stardew.craft.api.v1.internal.farm;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.farm.StardewFarmSelectionOptions;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Core client dispatch bridge. Not part of the public compatibility surface. */
public final class StardewFarmSelectionOptionRegistry {
    private static final Map<ResourceLocation, StardewFarmSelectionOptions.Option> OPTIONS =
            new HashMap<>();
    private static volatile List<StardewFarmSelectionOptions.Option> snapshot = List.of();

    private StardewFarmSelectionOptionRegistry() {
    }

    public static synchronized void register(StardewFarmSelectionOptions.Option option) {
        if (OPTIONS.containsKey(option.id())) {
            throw new IllegalStateException(
                    "Stardew farm selection option already registered: " + option.id());
        }
        OPTIONS.put(option.id(), option);
        ArrayList<StardewFarmSelectionOptions.Option> ordered =
                new ArrayList<>(OPTIONS.values());
        ordered.sort(Comparator.comparingInt(StardewFarmSelectionOptions.Option::priority)
                .reversed().thenComparing(value -> value.id().toString()));
        snapshot = List.copyOf(ordered);
    }

    public static List<StardewFarmSelectionOptions.Option> options() {
        return snapshot;
    }

    public static void dispatch(
            StardewFarmSelectionOptions.Option option,
            boolean selected,
            String farmTypeId,
            String farmName,
            boolean forceCancelPending
    ) {
        try {
            option.handler().selected(new StardewFarmSelectionOptions.Selection(
                    option.id(), selected, farmTypeId, farmName, forceCancelPending));
        } catch (RuntimeException exception) {
            StardewCraft.LOGGER.error(
                    "Stardew farm selection option {} failed while submitting",
                    option.id(),
                    exception
            );
        }
    }
}
