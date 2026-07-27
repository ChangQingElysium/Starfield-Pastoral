package com.stardew.craft.api.v1.building;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** One item cost in a building blueprint. */
public record StardewBuildingMaterial(
        ResourceLocation item,
        int count
) {
    public static final Codec<StardewBuildingMaterial> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    ResourceLocation.CODEC.fieldOf("item")
                            .forGetter(StardewBuildingMaterial::item),
                    Codec.intRange(1, 1_000_000).fieldOf("count")
                            .forGetter(StardewBuildingMaterial::count)
            ).apply(instance, StardewBuildingMaterial::new));

    public StardewBuildingMaterial {
        item = Objects.requireNonNull(item, "item");
        if (count < 1) {
            throw new IllegalArgumentException(
                    "building material count must be positive");
        }
    }
}
