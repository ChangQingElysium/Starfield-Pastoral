package com.stardew.craft.api.v1.agriculture;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

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
