package com.stardew.craft.api.v1.economy;

import com.stardew.craft.api.v1.internal.economy.StardewCurrencyRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.OptionalLong;

/** Registration, discovery and server-authoritative balance facade for currencies. */
public final class StardewCurrencies {
    public static final ResourceLocation MONEY =
            ResourceLocation.fromNamespaceAndPath("stardewcraft", "money");
    public static final ResourceLocation FAIR_STAR_TOKENS =
            ResourceLocation.fromNamespaceAndPath("stardewcraft", "fair_star_tokens");
    public static final ResourceLocation QI_COINS =
            ResourceLocation.fromNamespaceAndPath("stardewcraft", "qi_coins");

    private StardewCurrencies() {
    }

    public static void register(
            StardewCurrency currency,
            StardewCurrencyHandler handler
    ) {
        StardewCurrencyRegistry.register(currency, handler);
    }

    public static List<StardewCurrency> definitions() {
        return StardewCurrencyRegistry.definitions();
    }

    public static OptionalLong balance(
            ResourceLocation currencyId,
            ServerPlayer player
    ) {
        return StardewCurrencyRegistry.balance(currencyId, player);
    }

    public static boolean withdraw(
            ResourceLocation currencyId,
            ServerPlayer player,
            long amount
    ) {
        return StardewCurrencyRegistry.withdraw(
                currencyId, player, amount);
    }

    public static boolean deposit(
            ResourceLocation currencyId,
            ServerPlayer player,
            long amount
    ) {
        return StardewCurrencyRegistry.deposit(
                currencyId, player, amount);
    }
}
