package com.stardew.craft.api.v1.production;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.stardew.craft.api.v1.item.StardewItemDataApi;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/** A structured cooking ingredient matched by one item, one tag, or Stardew categories. */
public record StardewCookingIngredient(
        Optional<ResourceLocation> item,
        Optional<ResourceLocation> tag,
        List<ResourceLocation> categories,
        int count,
        Optional<ResourceLocation> displayItem
) {
    public static final Codec<StardewCookingIngredient> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            com.stardew.craft.port.PortCodecs.optionalFieldOf(ResourceLocation.CODEC, "item").forGetter(StardewCookingIngredient::item),
            com.stardew.craft.port.PortCodecs.optionalFieldOf(ResourceLocation.CODEC, "tag").forGetter(StardewCookingIngredient::tag),
            com.stardew.craft.port.PortCodecs.optionalFieldOf(ResourceLocation.CODEC.listOf(), "categories", List.of())
                    .forGetter(StardewCookingIngredient::categories),
            com.stardew.craft.port.PortCodecs.optionalFieldOf(Codec.intRange(1, Integer.MAX_VALUE), "count", 1)
                    .forGetter(StardewCookingIngredient::count),
            com.stardew.craft.port.PortCodecs.optionalFieldOf(ResourceLocation.CODEC, "display_item")
                    .forGetter(StardewCookingIngredient::displayItem)
    ).apply(instance, StardewCookingIngredient::new));

    public StardewCookingIngredient {
        categories = List.copyOf(categories);
        int selectors = (item.isPresent() ? 1 : 0) + (tag.isPresent() ? 1 : 0) + (categories.isEmpty() ? 0 : 1);
        if (selectors != 1) {
            throw new IllegalArgumentException("Cooking ingredient needs exactly one of item, tag, or categories");
        }
    }

    public boolean matches(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (item.isPresent()) {
            return item.get().equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        }
        if (tag.isPresent()) {
            return stack.is(TagKey.create(Registries.ITEM, tag.get()));
        }
        return StardewItemDataApi.resolve(stack)
                .map(data -> categories.contains(data.category()))
                .orElse(false);
    }

    /** Stable key used by the fridge-count sync payload. */
    public String matcherKey() {
        if (item.isPresent()) return "item:" + item.get();
        if (tag.isPresent()) return "tag:" + tag.get();
        return "categories:" + categories.stream().map(ResourceLocation::toString).collect(Collectors.joining(","));
    }
}
