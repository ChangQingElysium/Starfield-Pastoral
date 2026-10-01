package com.stardew.craft.api.v1.fishing;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.stardew.craft.api.v1.query.StardewItemQueries;
import com.stardew.craft.api.v1.query.StardewItemQuery;

/** A weighted item query inside an additive fishing treasure pool. */
public record StardewFishingTreasureEntry(
        StardewItemQuery query,
        int weight,
        int minFishingLevel,
        int maxFishingLevel,
        int minWaterDistance,
        int maxWaterDistance
) {
    public static final Codec<StardewFishingTreasureEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            StardewItemQueries.CODEC.fieldOf("query").forGetter(StardewFishingTreasureEntry::query),
            com.stardew.craft.port.PortCodecs.optionalFieldOf(Codec.intRange(1, Integer.MAX_VALUE), "weight", 1)
                    .forGetter(StardewFishingTreasureEntry::weight),
            com.stardew.craft.port.PortCodecs.optionalFieldOf(Codec.intRange(0, 100), "min_fishing_level", 0)
                    .forGetter(StardewFishingTreasureEntry::minFishingLevel),
            com.stardew.craft.port.PortCodecs.optionalFieldOf(Codec.intRange(0, 100), "max_fishing_level", 100)
                    .forGetter(StardewFishingTreasureEntry::maxFishingLevel),
            com.stardew.craft.port.PortCodecs.optionalFieldOf(Codec.intRange(0, 5), "min_water_distance", 0)
                    .forGetter(StardewFishingTreasureEntry::minWaterDistance),
            com.stardew.craft.port.PortCodecs.optionalFieldOf(Codec.intRange(0, 5), "max_water_distance", 5)
                    .forGetter(StardewFishingTreasureEntry::maxWaterDistance)
    ).apply(instance, StardewFishingTreasureEntry::new));

    public StardewFishingTreasureEntry {
        if (maxFishingLevel < minFishingLevel) {
            throw new IllegalArgumentException("max_fishing_level cannot be below min_fishing_level");
        }
        if (maxWaterDistance < minWaterDistance) {
            throw new IllegalArgumentException("max_water_distance cannot be below min_water_distance");
        }
    }

    public boolean isEligible(int fishingLevel, int waterDistance) {
        return fishingLevel >= minFishingLevel && fishingLevel <= maxFishingLevel
                && waterDistance >= minWaterDistance && waterDistance <= maxWaterDistance;
    }
}
