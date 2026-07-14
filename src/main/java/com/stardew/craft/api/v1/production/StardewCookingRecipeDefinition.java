package com.stardew.craft.api.v1.production;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Immutable datapack cooking recipe; the resource path supplies the recipe ID. */
public record StardewCookingRecipeDefinition(
        ResourceLocation output,
        int outputCount,
        List<StardewCookingIngredient> ingredients
) {
    public static final Codec<StardewCookingRecipeDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("output").forGetter(StardewCookingRecipeDefinition::output),
            Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("output_count", 1)
                    .forGetter(StardewCookingRecipeDefinition::outputCount),
            StardewCookingIngredient.CODEC.listOf().fieldOf("ingredients")
                    .forGetter(StardewCookingRecipeDefinition::ingredients)
    ).apply(instance, StardewCookingRecipeDefinition::new));

    public StardewCookingRecipeDefinition {
        ingredients = List.copyOf(ingredients);
        if (ingredients.isEmpty()) {
            throw new IllegalArgumentException("Cooking recipe needs at least one ingredient");
        }
    }
}
