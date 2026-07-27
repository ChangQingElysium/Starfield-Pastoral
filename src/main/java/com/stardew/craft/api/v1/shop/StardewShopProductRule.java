package com.stardew.craft.api.v1.shop;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.stardew.craft.api.v1.action.StardewAction;
import com.stardew.craft.api.v1.action.StardewActions;
import com.stardew.craft.api.v1.condition.StardewCondition;
import com.stardew.craft.api.v1.condition.StardewConditions;

import java.util.List;

/** Reloadable single-action virtual product for an exact shop row. */
public record StardewShopProductRule(
        String shop,
        String item,
        int priority,
        StardewAction action,
        List<StardewCondition> availableWhen
) {
    public static final Codec<StardewShopProductRule> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.STRING.fieldOf("shop")
                            .forGetter(StardewShopProductRule::shop),
                    Codec.STRING.fieldOf("item")
                            .forGetter(StardewShopProductRule::item),
                    Codec.INT.optionalFieldOf("priority", 0)
                            .forGetter(
                                    StardewShopProductRule::priority),
                    StardewActions.CODEC.fieldOf("action")
                            .forGetter(
                                    StardewShopProductRule::action),
                    StardewConditions.CODEC.listOf()
                            .optionalFieldOf(
                                    "available_when", List.of())
                            .forGetter(
                                    StardewShopProductRule
                                            ::availableWhen)
            ).apply(instance, StardewShopProductRule::new));

    public StardewShopProductRule {
        if (shop.isBlank()) {
            throw new IllegalArgumentException(
                    "shop must not be blank");
        }
        if (item.isBlank()) {
            throw new IllegalArgumentException(
                    "item must not be blank");
        }
        availableWhen = List.copyOf(availableWhen);
    }
}
