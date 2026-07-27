package com.stardew.craft.api.v1.shop;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.stardew.craft.api.v1.condition.StardewCondition;
import com.stardew.craft.api.v1.condition.StardewConditions;
import com.stardew.craft.api.v1.economy.StardewCost;
import com.stardew.craft.api.v1.economy.StardewCostEntry;
import com.stardew.craft.api.v1.economy.StardewCurrencyCost;
import com.stardew.craft.api.v1.economy.StardewItemCost;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Reloadable complete-cost override for matching shop rows. */
public record StardewShopCostRule(
        String shop,
        String item,
        int priority,
        List<CurrencyEntry> currencies,
        List<ItemEntry> items,
        List<StardewCondition> availableWhen
) {
    public static final Codec<StardewShopCostRule> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.STRING.fieldOf("shop")
                            .forGetter(StardewShopCostRule::shop),
                    Codec.STRING.fieldOf("item")
                            .forGetter(StardewShopCostRule::item),
                    Codec.INT.optionalFieldOf("priority", 0)
                            .forGetter(StardewShopCostRule::priority),
                    CurrencyEntry.CODEC.listOf()
                            .optionalFieldOf(
                                    "currencies", List.of())
                            .forGetter(
                                    StardewShopCostRule::currencies),
                    ItemEntry.CODEC.listOf()
                            .optionalFieldOf("items", List.of())
                            .forGetter(StardewShopCostRule::items),
                    StardewConditions.CODEC.listOf()
                            .optionalFieldOf(
                                    "available_when", List.of())
                            .forGetter(
                                    StardewShopCostRule::availableWhen)
            ).apply(instance, StardewShopCostRule::new));

    public StardewShopCostRule {
        currencies = List.copyOf(currencies);
        items = List.copyOf(items);
        availableWhen = List.copyOf(availableWhen);
        if (shop.isBlank() || item.isBlank()) {
            throw new IllegalArgumentException(
                    "shop and item must not be blank");
        }
        if (currencies.isEmpty() && items.isEmpty()) {
            throw new IllegalArgumentException(
                    "a shop cost rule must contain at least one cost");
        }
    }

    public StardewCost cost(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "quantity must be positive");
        }
        ArrayList<StardewCostEntry> costs =
                new ArrayList<>(
                        currencies.size() + items.size());
        for (CurrencyEntry entry : currencies) {
            costs.add(new StardewCurrencyCost(
                    entry.id(),
                    Math.multiplyExact(
                            entry.amount(), quantity)));
        }
        for (ItemEntry entry : items) {
            costs.add(new StardewItemCost(
                    entry.id(),
                    Math.multiplyExact(
                            entry.amount(), quantity)));
        }
        return new StardewCost(costs);
    }

    public record CurrencyEntry(
            ResourceLocation id,
            long amount
    ) {
        public static final Codec<CurrencyEntry> CODEC =
                RecordCodecBuilder.create(instance ->
                        instance.group(
                                ResourceLocation.CODEC
                                        .fieldOf("id")
                                        .forGetter(
                                                CurrencyEntry::id),
                                Codec.LONG.validate(value ->
                                                value > 0L
                                                        ? com.mojang.serialization
                                                                .DataResult
                                                                .success(
                                                                        value)
                                                        : com.mojang.serialization
                                                                .DataResult
                                                                .error(() ->
                                                                        "amount must be positive"))
                                        .fieldOf("amount")
                                        .forGetter(
                                                CurrencyEntry::amount)
                        ).apply(instance, CurrencyEntry::new));
    }

    public record ItemEntry(
            ResourceLocation id,
            long amount
    ) {
        public static final Codec<ItemEntry> CODEC =
                RecordCodecBuilder.create(instance ->
                        instance.group(
                                ResourceLocation.CODEC
                                        .fieldOf("id")
                                        .forGetter(ItemEntry::id),
                                Codec.LONG.validate(value ->
                                                value > 0L
                                                        && value
                                                        <= Integer.MAX_VALUE
                                                        ? com.mojang.serialization
                                                                .DataResult
                                                                .success(
                                                                        value)
                                                        : com.mojang.serialization
                                                                .DataResult
                                                                .error(() ->
                                                                        "item amount must be between 1 and Integer.MAX_VALUE"))
                                        .fieldOf("amount")
                                        .forGetter(ItemEntry::amount)
                        ).apply(instance, ItemEntry::new));
    }
}
