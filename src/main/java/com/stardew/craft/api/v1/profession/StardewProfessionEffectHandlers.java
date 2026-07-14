package com.stardew.craft.api.v1.profession;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Namespaced profession effect handlers registered by add-on mods. */
public final class StardewProfessionEffectHandlers {
    public static final ResourceLocation SELL_PRICE_MULTIPLIER =
            ResourceLocation.fromNamespaceAndPath("stardewcraft", "sell_price_multiplier");

    private static final Map<ResourceLocation, StardewProfessionEffectHandler> HANDLERS = new LinkedHashMap<>();

    private StardewProfessionEffectHandlers() {
    }

    public static synchronized void register(ResourceLocation id, StardewProfessionEffectHandler handler) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(handler, "handler");
        if (HANDLERS.putIfAbsent(id, handler) != null) {
            throw new IllegalStateException("Profession effect handler already registered: " + id);
        }
    }

    public static synchronized Optional<StardewProfessionEffectHandler> get(ResourceLocation id) {
        return Optional.ofNullable(HANDLERS.get(id));
    }

    public static double apply(ResourceLocation handlerId, ResourceLocation professionId,
                               ResourceLocation operation, @Nullable ServerPlayer player,
                               ItemStack stack, double value) {
        StardewProfessionEffectHandler handler;
        synchronized (StardewProfessionEffectHandlers.class) {
            handler = HANDLERS.get(handlerId);
        }
        if (handler == null) return value;
        return handler.apply(new StardewProfessionEffectContext(
                professionId, operation, player, stack == null ? ItemStack.EMPTY : stack, value));
    }
}
