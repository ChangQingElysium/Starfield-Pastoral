package com.stardew.craft.api.v1.agriculture;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

/**
 * Shared animal metadata.
 *
 * <p>Loaded, managed StardewCraft animals consume {@link #produce()} and
 * {@link #produceIntervalDays()} during daily production. Purchase price, maturity and building
 * type remain descriptive metadata because those decisions happen before an entity exists and
 * therefore cannot safely evaluate an entity-sensitive provider.
 */
public record StardewAnimalData(
        ResourceLocation buildingType,
        int purchasePrice,
        int daysToMature,
        ResourceLocation produce,
        int produceIntervalDays
) {
    public static final Codec<StardewAnimalData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("building_type").forGetter(StardewAnimalData::buildingType),
            Codec.intRange(0, Integer.MAX_VALUE).fieldOf("purchase_price").forGetter(StardewAnimalData::purchasePrice),
            Codec.intRange(0, Integer.MAX_VALUE).fieldOf("days_to_mature").forGetter(StardewAnimalData::daysToMature),
            ResourceLocation.CODEC.fieldOf("produce").forGetter(StardewAnimalData::produce),
            Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("produce_interval_days", 1)
                    .forGetter(StardewAnimalData::produceIntervalDays)
    ).apply(instance, StardewAnimalData::new));
}
