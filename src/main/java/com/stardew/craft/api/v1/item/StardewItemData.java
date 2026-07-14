package com.stardew.craft.api.v1.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * Static Stardew metadata attached to a Minecraft item.
 *
 * <p>Datapacks normally provide this through the
 * {@code stardewcraft:stardew_item_data} item Data Map. Addons may return the
 * same value from a provider when metadata depends on the {@code ItemStack}.
 */
public record StardewItemData(
        ResourceLocation category,
        int baseSellPrice,
        int edibility,
        int energy,
        int health,
        boolean hidden
) {
    public static final ResourceLocation UNKNOWN_CATEGORY =
            ResourceLocation.fromNamespaceAndPath("stardewcraft", "unknown");

    public static final Codec<StardewItemData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.optionalFieldOf("category", UNKNOWN_CATEGORY)
                    .forGetter(StardewItemData::category),
            Codec.intRange(-1, Integer.MAX_VALUE).optionalFieldOf("base_sell_price", -1)
                    .forGetter(StardewItemData::baseSellPrice),
            Codec.INT.optionalFieldOf("edibility", -300)
                    .forGetter(StardewItemData::edibility),
            Codec.INT.optionalFieldOf("energy", 0)
                    .forGetter(StardewItemData::energy),
            Codec.INT.optionalFieldOf("health", 0)
                    .forGetter(StardewItemData::health),
            Codec.BOOL.optionalFieldOf("hidden", false)
                    .forGetter(StardewItemData::hidden)
    ).apply(instance, StardewItemData::new));

    public StardewItemData {
        Objects.requireNonNull(category, "category");
        if (baseSellPrice < -1) {
            throw new IllegalArgumentException("baseSellPrice must be -1 or non-negative");
        }
    }

    public boolean isFood() {
        return edibility > -300 || energy != 0 || health != 0;
    }
}
