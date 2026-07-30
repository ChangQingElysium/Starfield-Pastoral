package com.stardew.craft.api.v1.interaction;

import com.stardew.craft.StardewCraft;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Ordered addon extension point for crosshair-side interaction hints. */
public final class StardewInteractionHints {
    private static volatile List<Entry> providers = List.of();

    private StardewInteractionHints() {
    }

    public static synchronized void register(
            ResourceLocation id,
            int priority,
            StardewInteractionHintProvider provider
    ) {
        if (id == null || provider == null) {
            throw new IllegalArgumentException(
                    "Interaction hint provider id and provider must not be null");
        }
        if (providers.stream().anyMatch(entry -> entry.id().equals(id))) {
            throw new IllegalStateException(
                    "Duplicate interaction hint provider: " + id);
        }
        ArrayList<Entry> next = new ArrayList<>(providers);
        next.add(new Entry(id, priority, provider));
        next.sort(Comparator.comparingInt(Entry::priority).reversed()
                .thenComparing(entry -> entry.id().toString()));
        providers = List.copyOf(next);
    }

    public static StardewInteractionHintDecision dispatch(
            StardewInteractionHintContext context
    ) {
        for (Entry entry : providers) {
            try {
                StardewInteractionHintDecision decision =
                        entry.provider().resolve(context);
                if (decision != null && decision.handled()) {
                    return decision;
                }
            } catch (RuntimeException exception) {
                StardewCraft.LOGGER.error(
                        "[Interaction hint] Provider {} failed at {}",
                        entry.id(), context.pos(), exception);
            }
        }
        return StardewInteractionHintDecision.pass();
    }

    private record Entry(
            ResourceLocation id,
            int priority,
            StardewInteractionHintProvider provider
    ) {
    }
}
