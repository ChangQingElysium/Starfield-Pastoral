package com.stardew.craft.api.v1.agriculture;

import com.stardew.craft.StardewCraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Ordered addon resolvers that map incubator inputs to managed-animal type IDs. */
public final class StardewAnimalIncubation {
    private static final Map<ResourceLocation, Registered> RESOLVERS = new HashMap<>();
    private static volatile List<Registered> snapshot = List.of();

    private StardewAnimalIncubation() {
    }

    public static synchronized void register(
            ResourceLocation id,
            int priority,
            Resolver resolver
    ) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(resolver, "resolver");
        if (RESOLVERS.containsKey(id)) {
            throw new IllegalStateException(
                    "Stardew animal incubation resolver already registered: " + id);
        }
        RESOLVERS.put(id, new Registered(id, priority, resolver));
        ArrayList<Registered> ordered = new ArrayList<>(RESOLVERS.values());
        ordered.sort(Comparator.comparingInt(Registered::priority).reversed()
                .thenComparing(value -> value.id().toString()));
        snapshot = List.copyOf(ordered);
    }

    /**
     * Resolves an addon incubator input.
     *
     * <p>Unknown IDs are rejected so a bad resolver cannot consume an egg and create an animal
     * record that has no entity projection.
     */
    @Nullable
    public static String resolve(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        for (Registered registered : snapshot) {
            try {
                String resolved = registered.resolver().resolve(stack);
                if (resolved == null || resolved.isBlank()) {
                    continue;
                }
                String normalized = resolved.trim().toLowerCase(Locale.ROOT);
                if (!StardewAnimalTypes.isKnown(normalized)) {
                    StardewCraft.LOGGER.error(
                            "Stardew animal incubation resolver {} returned unknown type {}",
                            registered.id(),
                            normalized
                    );
                    continue;
                }
                return normalized;
            } catch (RuntimeException exception) {
                StardewCraft.LOGGER.error(
                        "Stardew animal incubation resolver {} failed",
                        registered.id(),
                        exception
                );
            }
        }
        return null;
    }

    @FunctionalInterface
    public interface Resolver {
        @Nullable String resolve(ItemStack stack);
    }

    private record Registered(ResourceLocation id, int priority, Resolver resolver) {
    }
}
