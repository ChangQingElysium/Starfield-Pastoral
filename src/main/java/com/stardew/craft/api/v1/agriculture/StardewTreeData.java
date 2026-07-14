package com.stardew.craft.api.v1.agriculture;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Shared tree metadata.
 *
 * <p>StardewCraft fruit-tree roots consume {@link #product()}, {@link #productCount()},
 * {@link #maxStoredProduct()} and {@link #fruitSeasons()}. {@link #kind()} and
 * {@link #daysToMature()} remain descriptive metadata; attaching this record to an arbitrary block
 * does not create a fruit-tree lifecycle.
 */
public record StardewTreeData(
        ResourceLocation kind,
        int daysToMature,
        ResourceLocation product,
        int productCount,
        int maxStoredProduct,
        List<String> fruitSeasons
) {
    public static final Codec<StardewTreeData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("kind").forGetter(StardewTreeData::kind),
            Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("days_to_mature", 28)
                    .forGetter(StardewTreeData::daysToMature),
            ResourceLocation.CODEC.fieldOf("product").forGetter(StardewTreeData::product),
            Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("product_count", 1)
                    .forGetter(StardewTreeData::productCount),
            Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("max_stored_product", 3)
                    .forGetter(StardewTreeData::maxStoredProduct),
            Codec.STRING.listOf().optionalFieldOf("fruit_seasons", List.of())
                    .forGetter(StardewTreeData::fruitSeasons)
    ).apply(instance, StardewTreeData::new));

    public StardewTreeData {
        fruitSeasons = List.copyOf(fruitSeasons == null ? List.of() : fruitSeasons);
    }
}
