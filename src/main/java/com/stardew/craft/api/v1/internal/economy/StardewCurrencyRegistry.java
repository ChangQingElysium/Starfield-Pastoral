package com.stardew.craft.api.v1.internal.economy;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.economy.StardewCurrencies;
import com.stardew.craft.api.v1.economy.StardewCurrency;
import com.stardew.craft.api.v1.economy.StardewCurrencyHandler;
import com.stardew.craft.api.v1.internal.extension.OrderedExtensionRegistry;
import com.stardew.craft.player.PlayerStardewDataAPI;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/** Internal currency catalog and guarded balance dispatch. */
public final class StardewCurrencyRegistry {
    private static final OrderedExtensionRegistry<Registration> REGISTRY =
            new OrderedExtensionRegistry<>(
                    ResourceLocation.fromNamespaceAndPath(
                            StardewCraft.MODID, "currencies"));
    private static boolean bootstrapped;

    private StardewCurrencyRegistry() {
    }

    public static synchronized void bootstrap() {
        if (bootstrapped) {
            return;
        }
        registerInternal(
                new StardewCurrency(
                        StardewCurrencies.MONEY,
                        Component.translatable(
                                "stardewcraft.currency.money"),
                        new ItemStack(Items.GOLD_NUGGET),
                        Integer.MAX_VALUE),
                new StardewCurrencyHandler() {
                    @Override
                    public long balance(ServerPlayer player) {
                        return PlayerStardewDataAPI.getMoney(player);
                    }

                    @Override
                    public boolean withdraw(
                            ServerPlayer player,
                            long amount
                    ) {
                        return amount <= Integer.MAX_VALUE
                                && PlayerStardewDataAPI.removeMoney(
                                        player, (int) amount);
                    }

                    @Override
                    public boolean deposit(
                            ServerPlayer player,
                            long amount
                    ) {
                        long current =
                                PlayerStardewDataAPI.getMoney(player);
                        if (amount > Integer.MAX_VALUE - current) {
                            return false;
                        }
                        PlayerStardewDataAPI.addMoney(
                                player, (int) amount);
                        return true;
                    }
                });
        registerInternal(
                new StardewCurrency(
                        StardewCurrencies.FAIR_STAR_TOKENS,
                        Component.translatable(
                                "stardewcraft.currency.fair_star_tokens"),
                        new ItemStack(Items.NETHER_STAR),
                        Integer.MAX_VALUE),
                new StardewCurrencyHandler() {
                    @Override
                    public long balance(ServerPlayer player) {
                        return PlayerStardewDataAPI
                                .getFairStarTokens(player);
                    }

                    @Override
                    public boolean withdraw(
                            ServerPlayer player,
                            long amount
                    ) {
                        return amount <= Integer.MAX_VALUE
                                && PlayerStardewDataAPI
                                        .consumeFairStarTokens(
                                                player, (int) amount);
                    }

                    @Override
                    public boolean deposit(
                            ServerPlayer player,
                            long amount
                    ) {
                        long current = PlayerStardewDataAPI
                                .getFairStarTokens(player);
                        if (amount > Integer.MAX_VALUE - current) {
                            return false;
                        }
                        PlayerStardewDataAPI.addFairStarTokens(
                                player, (int) amount);
                        return true;
                    }
                });
        registerInternal(
                new StardewCurrency(
                        StardewCurrencies.QI_COINS,
                        Component.translatable(
                                "stardewcraft.currency.qi_coins"),
                        new ItemStack(Items.EMERALD),
                        Integer.MAX_VALUE),
                new StardewCurrencyHandler() {
                    @Override
                    public long balance(ServerPlayer player) {
                        return com.stardew.craft.player.PlayerDataManager
                                .getPlayerData(player).getClubCoins();
                    }

                    @Override
                    public boolean withdraw(ServerPlayer player, long amount) {
                        if (amount > Integer.MAX_VALUE) {
                            return false;
                        }
                        var data = com.stardew.craft.player.PlayerDataManager
                                .getPlayerData(player);
                        if (!data.consumeClubCoins((int) amount)) {
                            return false;
                        }
                        saveClubCoins(player, data);
                        return true;
                    }

                    @Override
                    public boolean deposit(ServerPlayer player, long amount) {
                        var data = com.stardew.craft.player.PlayerDataManager
                                .getPlayerData(player);
                        if (amount > Integer.MAX_VALUE - data.getClubCoins()) {
                            return false;
                        }
                        data.addClubCoins((int) amount);
                        saveClubCoins(player, data);
                        return true;
                    }
                });
        bootstrapped = true;
    }

    private static void saveClubCoins(
            ServerPlayer player,
            com.stardew.craft.player.PlayerStardewData data
    ) {
        com.stardew.craft.player.PlayerDataManager.get()
                .savePlayerData(player.getUUID(), data);
        com.stardew.craft.player.PlayerDataEventHandler
                .syncPlayerData(player, data);
    }

    public static synchronized void register(
            StardewCurrency currency,
            StardewCurrencyHandler handler
    ) {
        bootstrap();
        registerInternal(currency, handler);
    }

    private static void registerInternal(
            StardewCurrency currency,
            StardewCurrencyHandler handler
    ) {
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(handler, "handler");
        REGISTRY.register(
                currency.id(), 0,
                new Registration(currency, handler));
    }

    public static List<StardewCurrency> definitions() {
        bootstrap();
        return REGISTRY.entries().stream()
                .map(entry -> entry.extension().currency())
                .toList();
    }

    public static OptionalLong balance(
            ResourceLocation currencyId,
            ServerPlayer player
    ) {
        Optional<Registration> registration =
                registration(currencyId);
        if (registration.isEmpty() || player == null) {
            return OptionalLong.empty();
        }
        return readBalance(
                currencyId, registration.get(), player);
    }

    public static boolean withdraw(
            ResourceLocation currencyId,
            ServerPlayer player,
            long amount
    ) {
        if (amount <= 0L || player == null) {
            return false;
        }
        Optional<Registration> registration =
                registration(currencyId);
        if (registration.isEmpty()) {
            return false;
        }
        Registration account = registration.get();
        OptionalLong before =
                readBalance(currencyId, account, player);
        if (before.isEmpty() || before.getAsLong() < amount) {
            return false;
        }
        boolean claimedSuccess;
        try {
            claimedSuccess = account.handler()
                    .withdraw(player, amount);
        } catch (RuntimeException exception) {
            StardewCraft.LOGGER.error(
                    "Currency {} withdrawal failed",
                    currencyId, exception);
            return verifyMutation(
                    currencyId, account, player,
                    before.getAsLong(), before.getAsLong(),
                    false, "withdrawal");
        }
        long expected = claimedSuccess
                ? before.getAsLong() - amount
                : before.getAsLong();
        return verifyMutation(
                currencyId, account, player, before.getAsLong(),
                expected, claimedSuccess, "withdrawal");
    }

    public static boolean deposit(
            ResourceLocation currencyId,
            ServerPlayer player,
            long amount
    ) {
        if (amount <= 0L || player == null) {
            return false;
        }
        Optional<Registration> registration =
                registration(currencyId);
        if (registration.isEmpty()) {
            return false;
        }
        Registration account = registration.get();
        OptionalLong before =
                readBalance(currencyId, account, player);
        if (before.isEmpty()
                || amount > account.currency().maximumBalance()
                        - before.getAsLong()) {
            return false;
        }
        boolean claimedSuccess;
        try {
            claimedSuccess = account.handler()
                    .deposit(player, amount);
        } catch (RuntimeException exception) {
            StardewCraft.LOGGER.error(
                    "Currency {} deposit failed",
                    currencyId, exception);
            return verifyMutation(
                    currencyId, account, player,
                    before.getAsLong(), before.getAsLong(),
                    false, "deposit");
        }
        long expected = claimedSuccess
                ? before.getAsLong() + amount
                : before.getAsLong();
        return verifyMutation(
                currencyId, account, player, before.getAsLong(),
                expected, claimedSuccess, "deposit");
    }

    private static OptionalLong readBalance(
            ResourceLocation currencyId,
            Registration registration,
            ServerPlayer player
    ) {
        try {
            long balance =
                    registration.handler().balance(player);
            if (balance < 0L
                    || balance > registration.currency()
                            .maximumBalance()) {
                StardewCraft.LOGGER.error(
                        "Currency {} returned invalid balance {}",
                        currencyId, balance);
                return OptionalLong.empty();
            }
            return OptionalLong.of(balance);
        } catch (RuntimeException exception) {
            StardewCraft.LOGGER.error(
                    "Currency {} balance lookup failed",
                    currencyId, exception);
            return OptionalLong.empty();
        }
    }

    private static boolean verifyMutation(
            ResourceLocation currencyId,
            Registration account,
            ServerPlayer player,
            long before,
            long expected,
            boolean claimedSuccess,
            String operation
    ) {
        OptionalLong after =
                readBalance(currencyId, account, player);
        if (after.isPresent()
                && after.getAsLong() == expected) {
            return claimedSuccess;
        }
        StardewCraft.LOGGER.error(
                "Currency {} {} violated its atomic balance contract "
                        + "(before={}, expected={}, actual={}); "
                        + "attempting compensation",
                currencyId, operation, before, expected,
                after.isPresent()
                        ? Long.toString(after.getAsLong())
                        : "unavailable");
        if (after.isPresent()) {
            compensate(
                    currencyId, account, player,
                    before, after.getAsLong());
        }
        return false;
    }

    private static void compensate(
            ResourceLocation currencyId,
            Registration account,
            ServerPlayer player,
            long target,
            long actual
    ) {
        try {
            boolean compensated = actual < target
                    ? account.handler().deposit(
                            player, target - actual)
                    : account.handler().withdraw(
                            player, actual - target);
            OptionalLong restored =
                    readBalance(currencyId, account, player);
            if (!compensated || restored.isEmpty()
                    || restored.getAsLong() != target) {
                StardewCraft.LOGGER.error(
                        "Currency {} compensation failed "
                                + "(target={}, actual={})",
                        currencyId, target,
                        restored.isPresent()
                                ? restored.getAsLong()
                                : "unavailable");
            }
        } catch (RuntimeException exception) {
            StardewCraft.LOGGER.error(
                    "Currency {} compensation threw",
                    currencyId, exception);
        }
    }

    private static Optional<Registration> registration(
            ResourceLocation currencyId
    ) {
        bootstrap();
        if (currencyId == null) {
            return Optional.empty();
        }
        return REGISTRY.entries().stream()
                .filter(entry -> entry.id().equals(currencyId))
                .map(OrderedExtensionRegistry.Entry::extension)
                .findFirst();
    }

    private record Registration(
            StardewCurrency currency,
            StardewCurrencyHandler handler
    ) {
    }
}
