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
 * Ordered addon veto rules for managed-animal reproduction.
 *
 * <p>Rules run only after the animal is adult and its player-controlled reproduction toggle is
 * enabled. They cannot override that toggle or the core building capacity checks.
 */
public final class StardewAnimalReproductionRules {
    private static final Map<ResourceLocation, Registered> RULES = new HashMap<>();
    private static volatile Map<String, List<Registered>> snapshot = Map.of();

    private StardewAnimalReproductionRules() {
    }

    public static synchronized void register(
            ResourceLocation id,
            String animalTypeId,
            int priority,
            Rule rule
    ) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(rule, "rule");
        String normalizedTypeId = normalizeTypeId(animalTypeId);
        if (normalizedTypeId.isEmpty()) {
            throw new IllegalArgumentException("Animal type ID cannot be blank");
        }
        if (RULES.containsKey(id)) {
            throw new IllegalStateException(
                    "Stardew animal reproduction rule already registered: " + id);
        }

        RULES.put(id, new Registered(id, normalizedTypeId, priority, rule));
        Map<String, List<Registered>> orderedByType = new HashMap<>();
        for (Registered registered : RULES.values()) {
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

    public static boolean allows(StardewAnimalReproductionContext context) {
        Objects.requireNonNull(context, "context");
        List<Registered> registeredRules =
                snapshot.getOrDefault(normalizeTypeId(context.animalTypeId()), List.of());
        for (Registered registered : registeredRules) {
            try {
                if (registered.rule().evaluate(context) == Decision.DENY) {
                    return false;
                }
            } catch (RuntimeException exception) {
                StardewCraft.LOGGER.error(
                        "Stardew animal reproduction rule {} failed for animal {} ({})",
                        registered.id(),
                        context.animalId(),
                        context.animalTypeId(),
                        exception
                );
            }
        }
        return true;
    }

    private static String normalizeTypeId(String animalTypeId) {
        if (animalTypeId == null) {
            return "";
        }
        return animalTypeId.trim().toLowerCase(Locale.ROOT);
    }

    public enum Decision {
        PASS,
        DENY
    }

    @FunctionalInterface
    public interface Rule {
        Decision evaluate(StardewAnimalReproductionContext context);
    }

    private record Registered(
            ResourceLocation id,
            String animalTypeId,
            int priority,
            Rule rule
    ) {
    }
}
