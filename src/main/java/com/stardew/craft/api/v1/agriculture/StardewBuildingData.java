package com.stardew.craft.api.v1.agriculture;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record StardewBuildingData(
        ResourceLocation type,
        int capacity,
        List<ResourceLocation> acceptedAnimals,
        List<ResourceLocation> requiredInteriorBlocks
) {
    public static final Codec<StardewBuildingData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("type").forGetter(StardewBuildingData::type),
            Codec.intRange(0, Integer.MAX_VALUE).fieldOf("capacity").forGetter(StardewBuildingData::capacity),
            ResourceLocation.CODEC.listOf().optionalFieldOf("accepted_animals", List.of())
                    .forGetter(StardewBuildingData::acceptedAnimals),
            ResourceLocation.CODEC.listOf().optionalFieldOf("required_interior_blocks", List.of())
                    .forGetter(StardewBuildingData::requiredInteriorBlocks)
    ).apply(instance, StardewBuildingData::new));
}
