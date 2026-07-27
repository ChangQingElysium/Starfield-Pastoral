package com.stardew.craft.api.v1.internal.fishing;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.fishing.StardewFishingLocationKeys;
import com.stardew.craft.api.v1.internal.extension.OrderedExtensionRegistry;
import com.stardew.craft.api.v1.world.StardewLocations;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/** Internal fishing location-key dispatch. */
public final class StardewFishingLocationKeyRegistry {
    private static final OrderedExtensionRegistry<
            StardewFishingLocationKeys.Provider> PROVIDERS =
            new OrderedExtensionRegistry<>(
                    ResourceLocation.fromNamespaceAndPath(
                            StardewCraft.MODID, "fishing/location_keys"));

    private StardewFishingLocationKeyRegistry() {
    }

    public static void register(
            ResourceLocation id,
            int priority,
            StardewFishingLocationKeys.Provider provider
    ) {
        PROVIDERS.register(id, priority, provider);
    }

    public static List<String> resolve(
            ServerLevel level,
            Holder<Biome> biome,
            @Nullable BlockPos position,
            List<String> initial
    ) {
        List<String> current = includeLogicalLocation(
                level, position, normalize(initial));
        for (var registered : PROVIDERS.entries()) {
            try {
                List<String> proposedKeys = current;
                List<String> candidate = PROVIDERS.invoke(
                        registered,
                        provider -> provider.resolve(
                                new StardewFishingLocationKeys.Context(
                                        level,
                                        biome,
                                        position,
                                        proposedKeys)));
                if (candidate != null) {
                    current = normalize(candidate);
                }
            } catch (RuntimeException exception) {
                StardewCraft.LOGGER.error(
                        "Fishing location-key provider {} failed at {}",
                        registered.id(), position, exception);
            }
        }
        return current;
    }

    private static List<String> includeLogicalLocation(
            ServerLevel level,
            @Nullable BlockPos position,
            List<String> initial
    ) {
        if (position == null) {
            return initial;
        }
        return StardewLocations.find(level, position)
                .map(location -> {
                    ArrayList<String> keys = new ArrayList<>(initial);
                    for (var hierarchyLocation
                            : StardewLocations.hierarchy(location.id())) {
                        keys.add(hierarchyLocation.id().toString());
                        keys.addAll(hierarchyLocation.aliases());
                    }
                    return normalize(keys);
                })
                .orElse(initial);
    }

    private static List<String> normalize(List<String> keys) {
        Objects.requireNonNull(keys, "keys");
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String key : keys) {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException(
                        "Fishing location keys must not contain blank values");
            }
            normalized.add(key.trim());
        }
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(
                    "Fishing location key list must not be empty");
        }
        return List.copyOf(normalized);
    }

}
