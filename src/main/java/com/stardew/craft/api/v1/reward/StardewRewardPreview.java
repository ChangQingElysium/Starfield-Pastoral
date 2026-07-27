package com.stardew.craft.api.v1.reward;

import com.stardew.craft.api.v1.progress.StardewProgressKey;

import java.util.List;
import java.util.Objects;

/** Immutable reward description associated with one progress entry. */
public record StardewRewardPreview(
        StardewProgressKey progress,
        List<StardewRewardComponent> components,
        boolean exhaustive
) {
    public StardewRewardPreview {
        progress = Objects.requireNonNull(progress, "progress");
        components = List.copyOf(components);
    }
}
