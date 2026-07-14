package com.stardew.craft.api.v1.agriculture;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Shared building metadata resolved from the building manager block.
 *
 * <p>Coop and barn creation consume {@link #capacity()}, and animal acquisition enforces a
 * non-empty {@link #acceptedAnimals()} list. {@link #type()} and {@link #requiredInteriorBlocks()}
 * remain descriptive metadata: StardewCraft's scanned free-form interiors do not have a universal
 * mapping from an arbitrary manager block to a required-block placement rule.
 */
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

    public StardewBuildingData {
        acceptedAnimals = List.copyOf(acceptedAnimals == null ? List.of() : acceptedAnimals);
        requiredInteriorBlocks = List.copyOf(requiredInteriorBlocks == null ? List.of() : requiredInteriorBlocks);
    }
}
