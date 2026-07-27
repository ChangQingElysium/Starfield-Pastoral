package com.stardew.craft.api.v1.farm;

import com.stardew.craft.api.v1.internal.farm.StardewFarmSelectionOptionRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * Client-side, read-only boolean options shown by the core farm selection screen.
 *
 * <p>The handler does not mutate core farm creation. It should send an addon-owned payload when
 * the selected value must be persisted or validated by the server.
 *
 * @deprecated Register layout-scoped, server-validated fields through
 * {@link StardewFarmLayouts#register(StardewFarmLayout, int, java.util.List)}.
 * This compatibility facade remains for addons that own a separate payload.
 */
@Deprecated(forRemoval = false)
public final class StardewFarmSelectionOptions {
    private StardewFarmSelectionOptions() {
    }

    public static void register(
            ResourceLocation id,
            int priority,
            Component label,
            Component tooltip,
            boolean defaultSelected,
            SelectionHandler handler
    ) {
        StardewFarmSelectionOptionRegistry.register(new Option(
                id, priority, label, tooltip, defaultSelected, handler));
    }

    @FunctionalInterface
    public interface SelectionHandler {
        void selected(Selection selection);
    }

    public record Selection(
            ResourceLocation optionId,
            boolean selected,
            String farmTypeId,
            String farmName,
            boolean forceCancelPending
    ) {
        public Selection {
            optionId = Objects.requireNonNull(optionId, "optionId");
            farmTypeId = Objects.requireNonNull(farmTypeId, "farmTypeId");
            farmName = Objects.requireNonNull(farmName, "farmName");
        }
    }

    public record Option(
            ResourceLocation id,
            int priority,
            Component label,
            Component tooltip,
            boolean defaultSelected,
            SelectionHandler handler
    ) {
        public Option {
            id = Objects.requireNonNull(id, "id");
            label = Objects.requireNonNull(label, "label");
            tooltip = Objects.requireNonNull(tooltip, "tooltip");
            handler = Objects.requireNonNull(handler, "handler");
        }
    }
}
