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
            com.stardew.craft.port.PortCodecs.optionalFieldOf(Codec.intRange(0, Integer.MAX_VALUE), "background", 0)
                    .forGetter(StardewMailDefinition::background),
            com.stardew.craft.port.PortCodecs.optionalFieldOf(Codec.STRING, "custom_background_texture")
                    .forGetter(StardewMailDefinition::customBackgroundTexture),
            com.stardew.craft.port.PortCodecs.optionalFieldOf(Codec.STRING, "text_color").forGetter(StardewMailDefinition::textColor),
            com.stardew.craft.port.PortCodecs.optionalFieldOf(AttachedItem.CODEC.listOf(), "attached_items", List.of())
                    .forGetter(StardewMailDefinition::attachedItems),
            com.stardew.craft.port.PortCodecs.optionalFieldOf(Codec.intRange(0, Integer.MAX_VALUE), "money", 0)
                    .forGetter(StardewMailDefinition::money),
            com.stardew.craft.port.PortCodecs.optionalFieldOf(Codec.STRING, "learned_recipe").forGetter(StardewMailDefinition::learnedRecipe),
            com.stardew.craft.port.PortCodecs.optionalFieldOf(Codec.BOOL, "recipe_is_cooking", false)
                    .forGetter(StardewMailDefinition::recipeIsCooking),
            com.stardew.craft.port.PortCodecs.optionalFieldOf(ResourceLocation.CODEC, "quest").forGetter(StardewMailDefinition::quest),
            com.stardew.craft.port.PortCodecs.optionalFieldOf(ResourceLocation.CODEC, "special_order").forGetter(StardewMailDefinition::specialOrder),
            com.stardew.craft.port.PortCodecs.optionalFieldOf(StardewConditions.CODEC.listOf(), "available_when", List.of())
                    .forGetter(StardewMailDefinition::availableWhen),
            com.stardew.craft.port.PortCodecs.optionalFieldOf(StardewActions.CODEC.listOf(), "on_delivery", List.of())
                    .forGetter(StardewMailDefinition::onDelivery),
            com.stardew.craft.port.PortCodecs.optionalFieldOf(StardewActions.CODEC.listOf(), "on_read", List.of())
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
                com.stardew.craft.port.PortCodecs.optionalFieldOf(Codec.intRange(1, Integer.MAX_VALUE), "count", 1)
                        .forGetter(AttachedItem::count)
        ).apply(instance, AttachedItem::new));
    }
}
