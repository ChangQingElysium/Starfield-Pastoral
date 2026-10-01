package com.stardew.craft.api.v1.query;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.random.RandomGenerator;

/** Server state and deterministic random source available to an item query. */
public record StardewItemQueryContext(
        ServerLevel level,
        @Nullable ServerPlayer player,
        RandomGenerator random,
        java.util.Map<String, Double> parameters,
        java.util.function.Consumer<com.stardew.craft.api.v1.action.StardewAction> deferredActions
) {
    public StardewItemQueryContext {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(random, "random");
        parameters = java.util.Map.copyOf(parameters);
        Objects.requireNonNull(deferredActions, "deferredActions");
    }

    /** Queries never execute actions. Legacy/preview callers discard deferred effects. */
    public StardewItemQueryContext(ServerLevel level, @Nullable ServerPlayer player, RandomGenerator random) {
        this(level, player, random, java.util.Map.of(), action -> {});
    }

    public static StardewItemQueryContext forPlayer(ServerPlayer player, RandomGenerator random) {
        Objects.requireNonNull(player, "player");
        return new StardewItemQueryContext(player.serverLevel(), player, random);
    }
}
