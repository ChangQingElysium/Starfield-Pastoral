package com.stardew.craft.api.v1.world;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable named world point shared by maps, NPC schedules, portals and
 * festival content.
 */
public record StardewWorldAnchor(
        ResourceLocation id,
        ResourceLocation dimension,
        Vec3 position,
        float yaw,
        boolean indoor,
        boolean useGroundHeight,
        @Nullable ResourceLocation locationId,
        Set<ResourceLocation> roles
) {
    public StardewWorldAnchor {
        id = Objects.requireNonNull(id, "id");
        dimension = Objects.requireNonNull(dimension, "dimension");
        position = Objects.requireNonNull(position, "position");
        roles = java.util.Collections.unmodifiableSet(
                new java.util.LinkedHashSet<>(
                        roles == null ? Set.of() : roles));
        if (!Double.isFinite(position.x)
                || !Double.isFinite(position.y)
                || !Double.isFinite(position.z)
                || !Float.isFinite(yaw)) {
            throw new IllegalArgumentException(
                    "anchor position and yaw must be finite");
        }
    }

    public boolean hasRole(ResourceLocation role) {
        return roles.contains(role);
    }
}
