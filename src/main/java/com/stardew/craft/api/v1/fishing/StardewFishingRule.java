package com.stardew.craft.api.v1.fishing;

import java.util.List;
import java.util.Objects;

/** Immutable subset of a fishing spawn rule used by runtime condition providers. */
public record StardewFishingRule(
        String id,
        String itemId,
        List<String> locations,
        List<String> biomeTags,
        List<String> seasons,
        String weather,
        List<TimeRange> timeRanges,
        int minFishingLevel,
        int minDistanceFromShore,
        int maxDistanceFromShore,
        boolean requireMagicBait,
        int catchLimit,
        String condition
) {
    public StardewFishingRule {
        id = requireText(id, "id");
        itemId = requireText(itemId, "itemId");
        locations = List.copyOf(Objects.requireNonNull(locations, "locations"));
        biomeTags = List.copyOf(Objects.requireNonNull(biomeTags, "biomeTags"));
        seasons = List.copyOf(Objects.requireNonNull(seasons, "seasons"));
        weather = Objects.requireNonNull(weather, "weather");
        timeRanges = List.copyOf(Objects.requireNonNull(timeRanges, "timeRanges"));
        condition = Objects.requireNonNull(condition, "condition");
        if (minFishingLevel < 0) {
            throw new IllegalArgumentException(
                    "minFishingLevel must be non-negative");
        }
        if (minDistanceFromShore < 0
                || maxDistanceFromShore < -1
                || maxDistanceFromShore >= 0
                && maxDistanceFromShore < minDistanceFromShore) {
            throw new IllegalArgumentException("invalid shore-distance range");
        }
        if (catchLimit < -1) {
            throw new IllegalArgumentException("catchLimit must be -1 or non-negative");
        }
    }

    public record TimeRange(int start, int end) {
        public TimeRange {
            if (start < 0 || end <= start) {
                throw new IllegalArgumentException("invalid fishing time range");
            }
        }
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
