package com.stardew.craft.api.v1.economy;

import net.minecraft.server.level.ServerPlayer;

/**
 * Server-side balance adapter for an addon currency.
 *
 * <p>Implementations must be synchronous and must only mutate the supplied player's account.
 * A successful withdrawal must be exactly reversible by {@link #deposit}; the payment service
 * relies on that contract when a later component of a composite cost fails.
 */
public interface StardewCurrencyHandler {
    long balance(ServerPlayer player);

    boolean withdraw(ServerPlayer player, long amount);

    boolean deposit(ServerPlayer player, long amount);
}
