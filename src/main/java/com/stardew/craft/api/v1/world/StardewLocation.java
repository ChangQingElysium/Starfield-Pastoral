package com.stardew.craft.api.v1.world;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Immutable runtime view of a reloadable logical location. */
public record StardewLocation(
        ResourceLocation id,
        ResourceLocation dimension,
        BlockPos min,
        BlockPos max,
        String ledgerId,
        List<String> aliases,
        int priority,
        boolean indoor,
        @Nullable ResourceLocation parentId,
        Component displayName,
        Component description,
        @Nullable ResourceLocation iconTexture,
        Set<ResourceLocation> tags,
        Map<ResourceLocation, String> properties
) {
    /** Backward-compatible constructor for locations without hierarchy metadata. */
    public StardewLocation(
            ResourceLocation id,
            ResourceLocation dimension,
            BlockPos min,
            BlockPos max,
            String ledgerId,
            List<String> aliases,
            int priority,
            boolean indoor
    ) {
        this(id, dimension, min, max, ledgerId, aliases, priority,
                indoor, null, Component.empty(), Component.empty(),
                null, Set.of(), Map.of());
    }

    /** Backward-compatible constructor for locations created before environment metadata. */
    public StardewLocation(
            ResourceLocation id,
            ResourceLocation dimension,
            BlockPos min,
            BlockPos max,
            String ledgerId,
            List<String> aliases,
            int priority
    ) {
        this(id, dimension, min, max, ledgerId, aliases, priority,
                false, null, Component.empty(), Component.empty(),
                null, Set.of(), Map.of());
    }

    public StardewLocation {
        id = Objects.requireNonNull(id, "id");
        dimension = Objects.requireNonNull(dimension, "dimension");
        min = Objects.requireNonNull(min, "min").immutable();
        max = Objects.requireNonNull(max, "max").immutable();
        ledgerId = ledgerId == null ? "" : ledgerId.trim();
        LinkedHashSet<String> normalizedAliases = new LinkedHashSet<>();
        for (String alias : aliases == null ? List.<String>of() : aliases) {
            if (alias != null && !alias.isBlank()) {
                normalizedAliases.add(alias.trim());
            }
        }
        aliases = List.copyOf(normalizedAliases);
        displayName = Objects.requireNonNull(
                displayName, "displayName");
        description = Objects.requireNonNull(
                description, "description");
        tags = java.util.Collections.unmodifiableSet(
                new LinkedHashSet<>(
                        tags == null ? Set.of() : tags));
        LinkedHashMap<ResourceLocation, String> normalizedProperties =
                new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, String> entry
                : (properties == null
                        ? Map.<ResourceLocation, String>of()
                        : properties).entrySet()) {
            normalizedProperties.put(
                    Objects.requireNonNull(
                            entry.getKey(), "property key"),
                    Objects.requireNonNull(
                            entry.getValue(), "property value"));
        }
        properties = java.util.Collections.unmodifiableMap(
                normalizedProperties);
        if (max.getX() < min.getX()
                || max.getY() < min.getY()
                || max.getZ() < min.getZ()) {
            throw new IllegalArgumentException(
                    "location max values must be >= min values");
        }
    }

    public boolean contains(
            ResourceLocation queriedDimension,
            BlockPos position
    ) {
        return dimension.equals(queriedDimension)
                && position.getX() >= min.getX()
                && position.getX() <= max.getX()
                && position.getY() >= min.getY()
                && position.getY() <= max.getY()
                && position.getZ() >= min.getZ()
                && position.getZ() <= max.getZ();
    }

    public boolean hasTag(ResourceLocation tag) {
        return tags.contains(tag);
    }

    public Optional<String> property(ResourceLocation key) {
        return Optional.ofNullable(properties.get(key));
    }
}
