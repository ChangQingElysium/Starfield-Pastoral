package com.stardew.craft.api.v1.production;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/** One counted crafting ingredient selected by item or item tag. */
public record StardewCraftingIngredient(
        Optional<ResourceLocation> item,
        Optional<ResourceLocation> tag,
        int count,
        Optional<ResourceLocation> displayItem,
        Optional<String> displayName
) {
    public static final Codec<StardewCraftingIngredient> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            com.stardew.craft.port.PortCodecs.optionalFieldOf(ResourceLocation.CODEC, "item").forGetter(StardewCraftingIngredient::item),
            com.stardew.craft.port.PortCodecs.optionalFieldOf(ResourceLocation.CODEC, "tag").forGetter(StardewCraftingIngredient::tag),
            com.stardew.craft.port.PortCodecs.optionalFieldOf(Codec.intRange(1, Integer.MAX_VALUE), "count", 1)
                    .forGetter(StardewCraftingIngredient::count),
            com.stardew.craft.port.PortCodecs.optionalFieldOf(ResourceLocation.CODEC, "display_item")
                    .forGetter(StardewCraftingIngredient::displayItem),
            com.stardew.craft.port.PortCodecs.optionalFieldOf(Codec.STRING, "display_name").forGetter(StardewCraftingIngredient::displayName)
    ).apply(instance, StardewCraftingIngredient::new));

    public StardewCraftingIngredient {
        if (item.isPresent() == tag.isPresent()) {
            throw new IllegalArgumentException("Crafting ingredient needs exactly one of item or tag");
        }
    }
}
