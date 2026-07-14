package com.stardew.craft.api.v1.mail;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.stardew.craft.api.v1.action.StardewAction;
import com.stardew.craft.api.v1.action.StardewActions;
import com.stardew.craft.api.v1.condition.StardewCondition;
import com.stardew.craft.api.v1.condition.StardewConditions;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/** Immutable, server-authoritative mail definition loaded from a datapack. */
public record StardewMailDefinition(
        String text,
        int background,
        Optional<String> customBackgroundTexture,
        Optional<String> textColor,
        List<AttachedItem> attachedItems,
        int money,
        Optional<String> learnedRecipe,
        boolean recipeIsCooking,
        Optional<ResourceLocation> quest,
        Optional<ResourceLocation> specialOrder,
        List<StardewCondition> availableWhen,
        List<StardewAction> onDelivery,
        List<StardewAction> onRead
) {
    public static final Codec<StardewMailDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("text").forGetter(StardewMailDefinition::text),
            Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("background", 0)
                    .forGetter(StardewMailDefinition::background),
            Codec.STRING.optionalFieldOf("custom_background_texture")
                    .forGetter(StardewMailDefinition::customBackgroundTexture),
            Codec.STRING.optionalFieldOf("text_color").forGetter(StardewMailDefinition::textColor),
            AttachedItem.CODEC.listOf().optionalFieldOf("attached_items", List.of())
                    .forGetter(StardewMailDefinition::attachedItems),
            Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("money", 0)
                    .forGetter(StardewMailDefinition::money),
            Codec.STRING.optionalFieldOf("learned_recipe").forGetter(StardewMailDefinition::learnedRecipe),
            Codec.BOOL.optionalFieldOf("recipe_is_cooking", false)
                    .forGetter(StardewMailDefinition::recipeIsCooking),
            ResourceLocation.CODEC.optionalFieldOf("quest").forGetter(StardewMailDefinition::quest),
            ResourceLocation.CODEC.optionalFieldOf("special_order").forGetter(StardewMailDefinition::specialOrder),
            StardewConditions.CODEC.listOf().optionalFieldOf("available_when", List.of())
                    .forGetter(StardewMailDefinition::availableWhen),
            StardewActions.CODEC.listOf().optionalFieldOf("on_delivery", List.of())
                    .forGetter(StardewMailDefinition::onDelivery),
            StardewActions.CODEC.listOf().optionalFieldOf("on_read", List.of())
                    .forGetter(StardewMailDefinition::onRead)
    ).apply(instance, StardewMailDefinition::new));

    public StardewMailDefinition {
        attachedItems = List.copyOf(attachedItems);
        availableWhen = List.copyOf(availableWhen);
        onDelivery = List.copyOf(onDelivery);
        onRead = List.copyOf(onRead);
    }

    public record AttachedItem(ResourceLocation item, int count) {
        public static final Codec<AttachedItem> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("id").forGetter(AttachedItem::item),
                Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("count", 1)
                        .forGetter(AttachedItem::count)
        ).apply(instance, AttachedItem::new));
    }
}
