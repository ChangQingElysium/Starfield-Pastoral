package com.stardew.craft.shop;

import com.stardew.craft.api.v1.economy.StardewCost;
import com.stardew.craft.api.v1.economy.StardewCostEntry;
import com.stardew.craft.api.v1.economy.StardewCurrencyCost;
import com.stardew.craft.api.v1.economy.StardewItemCost;
import com.stardew.craft.api.v1.internal.shop.StardewShopCostRegistry;
import com.stardew.craft.api.v1.condition.StardewConditionContext;
import com.stardew.craft.api.v1.condition.StardewConditions;
import com.stardew.craft.api.v1.shop.StardewShopCostContext;
import com.stardew.craft.api.v1.shop.StardewShopEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Compatibility bridge from legacy shop price fields to extensible composite costs. */
public final class ShopCostService {
    private ShopCostService() {
    }

    public static Optional<ResolvedCost> resolve(
            ServerPlayer player,
            String shopId,
            ShopItemEntry entry,
            int quantity,
            ResourceLocation defaultCurrency
    ) {
        Optional<StardewCost> defaultCost =
                legacyCost(entry, quantity, defaultCurrency);
        if (defaultCost.isEmpty()) {
            return Optional.empty();
        }
        StardewCost proposed = defaultCost.get();
        boolean dataModified = false;
        var conditionContext =
                StardewConditionContext.forPlayer(player);
        var rules = ShopDataLoader.costRuleSnapshot()
                .definitions().entrySet().stream()
                .filter(candidate -> candidate.getValue().shop()
                        .equals(shopId))
                .filter(candidate -> candidate.getValue().item()
                        .equals(entry.itemId()))
                .sorted(Comparator
                        .<java.util.Map.Entry<
                                ResourceLocation,
                                com.stardew.craft.api.v1.shop
                                        .StardewShopCostRule>>
                                comparingInt(candidate ->
                                        candidate.getValue()
                                                .priority())
                        .reversed()
                        .thenComparing(candidate ->
                                candidate.getKey().toString()))
                .toList();
        for (var candidate : rules) {
            boolean available = candidate.getValue()
                    .availableWhen().stream()
                    .allMatch(condition -> StardewConditions
                            .test(condition, conditionContext)
                            .result().orElse(false));
            if (!available) {
                continue;
            }
            try {
                StardewCost replacement =
                        candidate.getValue().cost(quantity);
                dataModified = !replacement.equals(proposed);
                proposed = replacement;
                break;
            } catch (RuntimeException exception) {
                com.stardew.craft.StardewCraft.LOGGER.error(
                        "Shop cost rule {} failed for {} / {}",
                        candidate.getKey(), shopId,
                        entry.itemId(), exception);
            }
        }
        var resolution = StardewShopCostRegistry.resolve(
                new StardewShopCostContext(
                        player, shopId, toApiEntry(entry), quantity),
                proposed);
        return Optional.of(new ResolvedCost(
                resolution.cost(),
                dataModified || resolution.modified()));
    }

    public static Optional<StardewCost> legacyCost(
            ShopItemEntry entry,
            int quantity,
            ResourceLocation defaultCurrency
    ) {
        if (entry == null || quantity <= 0
                || defaultCurrency == null) {
            return Optional.empty();
        }
        ArrayList<StardewCostEntry> entries =
                new ArrayList<>(2);
        long currencyAmount =
                (long) entry.price() * quantity;
        if (currencyAmount > 0L) {
            entries.add(new StardewCurrencyCost(
                    defaultCurrency, currencyAmount));
        }
        if (entry.requiresTrade()) {
            try {
                long itemAmount = Math.multiplyExact(
                        (long) Math.max(
                                1, entry.tradeItemCount()),
                        quantity);
                entries.add(new StardewItemCost(
                        ResourceLocation.parse(
                                entry.tradeItemId()),
                        itemAmount));
            } catch (IllegalArgumentException
                     | ArithmeticException ignored) {
                return Optional.empty();
            }
        }
        return Optional.of(new StardewCost(entries));
    }

    public static StardewShopEntry toApiEntry(
            ShopItemEntry entry
    ) {
        return new StardewShopEntry(
                entry.itemId(),
                entry.displayName(),
                entry.description(),
                entry.price(),
                entry.stock(),
                Optional.ofNullable(entry.tradeItemId())
                        .filter(id -> !id.isBlank()),
                entry.tradeItemCount(),
                List.copyOf(entry.seasons()),
                entry.minYear(),
                entry.minMineLevel(),
                Optional.ofNullable(entry.mailFlag())
                        .filter(flag -> !flag.isBlank()),
                entry.dayOfWeek(),
                entry.dayOfMonthParity(),
                entry.purchaseStack(),
                entry.availableWhen());
    }

    public record ResolvedCost(
            StardewCost cost,
            boolean modified
    ) {
    }
}
