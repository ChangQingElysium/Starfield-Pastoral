package com.stardew.craft.api.v1.mapinteraction;

import com.stardew.craft.StardewCraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Ordered Java addon extension point which runs before data-pack map
 * interaction definitions.
 */
public final class StardewMapInteractions {
    private static volatile List<Entry> providers = List.of();

    private StardewMapInteractions() {
    }

    public static synchronized void register(
            ResourceLocation id,
            int priority,
            StardewMapInteractionProvider provider
    ) {
        if (id == null || provider == null) {
            throw new IllegalArgumentException(
                    "Map interaction provider id and provider must not be null");
        }
        if (providers.stream().anyMatch(entry -> entry.id().equals(id))) {
            throw new IllegalStateException(
                    "Duplicate map interaction provider: " + id);
        }
        ArrayList<Entry> next = new ArrayList<>(providers);
        next.add(new Entry(id, priority, provider));
        next.sort(Comparator.comparingInt(Entry::priority).reversed()
                .thenComparing(entry -> entry.id().toString()));
        providers = List.copyOf(next);
    }

    public static InteractionResult dispatch(
            StardewMapInteractionContext context
    ) {
        for (Entry entry : providers) {
            try {
                InteractionResult result =
                        entry.provider().interact(context);
                if (result != null && result != InteractionResult.PASS) {
                    return result;
                }
            } catch (RuntimeException exception) {
                StardewCraft.LOGGER.error(
                        "[Map interaction] Provider {} failed at {}",
                        entry.id(), context.hit().getBlockPos(), exception);
            }
        }
        return InteractionResult.PASS;
    }

    private record Entry(
            ResourceLocation id,
            int priority,
            StardewMapInteractionProvider provider
    ) {
    }
}
