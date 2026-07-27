package com.stardew.craft.api.v1.agriculture;

import com.stardew.craft.StardewCraft;
import net.minecraft.resources.ResourceLocation;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Ordered extension point for rare replacements such as Stardew Valley's Truffle Crab. */
public final class StardewTruffleFoundHandlers {
    private static final Map<ResourceLocation, Registered> HANDLERS =
            new LinkedHashMap<>();
    private static volatile List<Registered> snapshot = List.of();

    private StardewTruffleFoundHandlers() {
    }

    public static synchronized void register(
            ResourceLocation id,
            int priority,
            Handler handler
    ) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(handler, "handler");
        if (HANDLERS.putIfAbsent(
                id,
                new Registered(id, priority, handler)
        ) != null) {
            throw new IllegalStateException(
                    "Truffle found handler already registered: " + id);
        }
        snapshot = HANDLERS.values().stream()
                .sorted(Comparator.comparingInt(Registered::priority)
                        .reversed()
                        .thenComparing(value -> value.id().toString()))
                .toList();
    }

    public static Result run(StardewTruffleFoundContext context) {
        Objects.requireNonNull(context, "context");
        for (Registered registered : snapshot) {
            try {
                if (registered.handler().handle(context)
                        == Result.REPLACE_TRUFFLE) {
                    return Result.REPLACE_TRUFFLE;
                }
            } catch (RuntimeException exception) {
                StardewCraft.LOGGER.error(
                        "Truffle found handler {} failed for animal {}",
                        registered.id(),
                        context.animalId(),
                        exception
                );
            }
        }
        return Result.PASS;
    }

    public enum Result {
        PASS,
        REPLACE_TRUFFLE
    }

    @FunctionalInterface
    public interface Handler {
        Result handle(StardewTruffleFoundContext context);
    }

    private record Registered(
            ResourceLocation id,
            int priority,
            Handler handler
    ) {
    }
}
