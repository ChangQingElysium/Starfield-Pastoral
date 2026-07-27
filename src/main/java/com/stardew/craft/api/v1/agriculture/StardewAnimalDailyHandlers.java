package com.stardew.craft.api.v1.agriculture;

import com.stardew.craft.StardewCraft;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Ordered daily lifecycle handlers for managed animals.
 *
 * <p>Handlers are scoped to an animal type. They run immediately after the core produce cooldown
 * is incremented and may update growth/care state or create custom produce. Returning
 * {@link Result#SKIP_DEFAULT_PRODUCTION} only replaces the core produce branch; core care, mood,
 * feeding and save processing still run.
 */
public final class StardewAnimalDailyHandlers {
    private static final Map<ResourceLocation, Registered> HANDLERS = new HashMap<>();
    private static volatile Map<String, List<Registered>> snapshot = Map.of();

    private StardewAnimalDailyHandlers() {
    }

    public static synchronized void register(
            ResourceLocation id,
            String animalTypeId,
            int priority,
            Handler handler
    ) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(handler, "handler");
        String normalizedTypeId = normalizeTypeId(animalTypeId);
        if (normalizedTypeId.isEmpty()) {
            throw new IllegalArgumentException("Animal type ID cannot be blank");
        }
        if (HANDLERS.containsKey(id)) {
            throw new IllegalStateException("Stardew animal daily handler already registered: " + id);
        }

        HANDLERS.put(id, new Registered(id, normalizedTypeId, priority, handler));
        Map<String, List<Registered>> orderedByType = new HashMap<>();
        for (Registered registered : HANDLERS.values()) {
            orderedByType.computeIfAbsent(registered.animalTypeId(), ignored -> new ArrayList<>())
                    .add(registered);
        }
        Comparator<Registered> ordering = Comparator.comparingInt(Registered::priority).reversed()
                .thenComparing(value -> value.id().toString());
        orderedByType.replaceAll((ignored, entries) -> {
            entries.sort(ordering);
            return List.copyOf(entries);
        });
        snapshot = Map.copyOf(orderedByType);
    }

    public static Result run(StardewAnimalDailyContext context) {
        Objects.requireNonNull(context, "context");
        List<Registered> registeredHandlers =
                snapshot.getOrDefault(normalizeTypeId(context.animalTypeId()), List.of());
        for (Registered registered : registeredHandlers) {
            try {
                Result result = registered.handler().handle(context);
                if (result == Result.SKIP_DEFAULT_PRODUCTION) {
                    return result;
                }
            } catch (RuntimeException exception) {
                StardewCraft.LOGGER.error(
                        "Stardew animal daily handler {} failed for animal {} ({})",
                        registered.id(),
                        context.animalId(),
                        context.animalTypeId(),
                        exception
                );
            }
        }
        return Result.PASS;
    }

    private static String normalizeTypeId(String animalTypeId) {
        if (animalTypeId == null) {
            return "";
        }
        return animalTypeId.trim().toLowerCase(Locale.ROOT);
    }

    public enum Result {
        PASS,
        SKIP_DEFAULT_PRODUCTION
    }

    @FunctionalInterface
    public interface Handler {
        Result handle(StardewAnimalDailyContext context);
    }

    private record Registered(
            ResourceLocation id,
            String animalTypeId,
            int priority,
            Handler handler
    ) {
    }
}
