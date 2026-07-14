package com.stardew.craft.api.v1.mining;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Ordered add-on hook evaluated before StardewCraft's vanilla-parity monster selector. */
public final class StardewMineMonsterProviders {
    private static final List<Registered> PROVIDERS = new ArrayList<>();

    private StardewMineMonsterProviders() {
    }

    public static synchronized void register(
            ResourceLocation id,
            int priority,
            StardewMineMonsterProvider provider
    ) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(provider, "provider");
        if (PROVIDERS.stream().anyMatch(entry -> entry.id().equals(id))) {
            throw new IllegalStateException("Mine monster provider already registered: " + id);
        }
        PROVIDERS.add(new Registered(id, priority, provider));
        PROVIDERS.sort(Comparator.comparingInt(Registered::priority).reversed()
                .thenComparing(entry -> entry.id().toString()));
    }

    @Nullable
    public static synchronized EntityType<?> select(StardewMineMonsterContext context) {
        for (Registered registered : PROVIDERS) {
            EntityType<?> selected = registered.provider().select(context);
            if (selected != null) return selected;
        }
        return null;
    }

    private record Registered(ResourceLocation id, int priority, StardewMineMonsterProvider provider) {
    }
}
