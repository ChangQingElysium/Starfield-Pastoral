package com.stardew.craft.api.v1.world;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * A resolved, read-only world point shared by map layouts and runtime
 * instances.
 *
 * <p>{@code scopeType + scopeId + id} is the stable compound identity.
 * The slot never grants permission to mutate or place world content.
 */
public record StardewMapSlot(
        ResourceLocation id,
        ResourceLocation dimension,
        Vec3 position,
        float yaw,
        boolean indoor,
        boolean useGroundHeight,
        ResourceLocation scopeType,
        String scopeId,
        @Nullable ResourceLocation containerId,
        Set<ResourceLocation> roles
) {
    public StardewMapSlot {
        id = Objects.requireNonNull(id, "id");
        dimension = Objects.requireNonNull(dimension, "dimension");
        position = Objects.requireNonNull(position, "position");
        scopeType = Objects.requireNonNull(
                scopeType, "scopeType");
        scopeId = Objects.requireNonNull(scopeId, "scopeId").trim();
        if (scopeId.isEmpty()) {
            throw new IllegalArgumentException(
                    "Map slot scope ID must not be blank");
        }
        roles = java.util.Collections.unmodifiableSet(
                new LinkedHashSet<>(
                        Objects.requireNonNull(roles, "roles")));
        if (!Double.isFinite(position.x)
                || !Double.isFinite(position.y)
                || !Double.isFinite(position.z)
                || !Float.isFinite(yaw)) {
            throw new IllegalArgumentException(
                    "Map slot position and yaw must be finite");
        }
    }

    public boolean hasRole(ResourceLocation role) {
        return roles.contains(role);
    }

    public BlockPos blockPosition() {
        return BlockPos.containing(position);
    }
}
