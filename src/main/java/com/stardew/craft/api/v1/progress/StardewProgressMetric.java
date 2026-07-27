package com.stardew.craft.api.v1.progress;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** One bounded numeric objective in a progress snapshot. */
public record StardewProgressMetric(ResourceLocation id, int current, int target) {
    public StardewProgressMetric {
        id = Objects.requireNonNull(id, "id");
        if (current < 0 || target < 1 || current > target) {
            throw new IllegalArgumentException("Progress metric must satisfy 0 <= current <= target");
        }
    }
}
