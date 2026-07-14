package com.stardew.craft.api.v1.production;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.stardew.craft.api.v1.condition.StardewCondition;
import com.stardew.craft.api.v1.condition.StardewConditions;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/** Immutable crafting recipe definition loaded from a datapack. */
public record StardewCraftingRecipeDefinition(
        ResourceLocation output,
        int outputCount,
        List<StardewCraftingIngredient> ingredients,
        List<StardewCondition> unlockWhen,
        Optional<String> legacyUnlockCondition
) {
    public static final Codec<StardewCraftingRecipeDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("output").forGetter(StardewCraftingRecipeDefinition::output),
            Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("output_count", 1)
                    .forGetter(StardewCraftingRecipeDefinition::outputCount),
            StardewCraftingIngredient.CODEC.listOf().fieldOf("ingredients")
                    .forGetter(StardewCraftingRecipeDefinition::ingredients),
            StardewConditions.CODEC.listOf().optionalFieldOf("unlock_when", List.of())
                    .forGetter(StardewCraftingRecipeDefinition::unlockWhen),
            Codec.STRING.optionalFieldOf("legacy_unlock_condition")
                    .forGetter(StardewCraftingRecipeDefinition::legacyUnlockCondition)
    ).apply(instance, StardewCraftingRecipeDefinition::new));

    public StardewCraftingRecipeDefinition {
        ingredients = List.copyOf(ingredients);
        unlockWhen = List.copyOf(unlockWhen);
        if (ingredients.isEmpty()) {
            throw new IllegalArgumentException("Crafting recipe needs at least one ingredient");
        }
    }
}
