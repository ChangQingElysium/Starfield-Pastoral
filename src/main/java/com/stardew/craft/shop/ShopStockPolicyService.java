package com.stardew.craft.shop;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.condition.StardewConditionContext;
import com.stardew.craft.api.v1.condition.StardewConditions;
import com.stardew.craft.api.v1.shop.StardewShopStockRule;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Comparator;

/** Resolves the effective limited-stock policy without changing legacy shop entry fields. */
public final class ShopStockPolicyService {
    public static final Policy DEFAULT = new Policy(
            StardewShopStockRule.Scope.PLAYER,
            StardewShopStockRule.Reset.DAY);

    private ShopStockPolicyService() {
    }

    public static Policy resolve(
            ServerPlayer player,
            String shopId,
            String itemId
    ) {
        var conditionContext =
                StardewConditionContext.forPlayer(player);
        var rules = ShopDataLoader.stockRuleSnapshot()
                .definitions().entrySet().stream()
                .filter(entry -> entry.getValue().shop()
                        .equals(shopId))
                .filter(entry -> entry.getValue().item()
                        .equals(itemId))
                .sorted(Comparator
                        .<java.util.Map.Entry<
                                ResourceLocation,
                                StardewShopStockRule>>
                                comparingInt(entry ->
                                        entry.getValue().priority())
                        .reversed()
                        .thenComparing(entry ->
                                entry.getKey().toString()))
                .toList();
        for (var entry : rules) {
            try {
                boolean available = entry.getValue()
                        .availableWhen().stream()
                        .allMatch(condition -> StardewConditions
                                .test(condition, conditionContext)
                                .result().orElse(false));
                if (available) {
                    return new Policy(
                            entry.getValue().scope(),
                            entry.getValue().reset());
                }
            } catch (RuntimeException exception) {
                StardewCraft.LOGGER.error(
                        "Shop stock rule {} failed for {} / {}",
                        entry.getKey(), shopId, itemId, exception);
            }
        }
        return DEFAULT;
    }

    public record Policy(
            StardewShopStockRule.Scope scope,
            StardewShopStockRule.Reset reset
    ) {
    }
}
