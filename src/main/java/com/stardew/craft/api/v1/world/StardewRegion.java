package com.stardew.craft.api.v1.world;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable union/subtraction geometry shared by logical locations and
 * coordinate-aware addon systems.
 */
public record StardewRegion(
        ResourceLocation id,
        ResourceLocation dimension,
        @Nullable ResourceLocation locationId,
        List<Box> includes,
        List<Box> excludes,
        Set<ResourceLocation> tags,
        int priority
) {
    public StardewRegion {
        id = Objects.requireNonNull(id, "id");
        dimension = Objects.requireNonNull(dimension, "dimension");
        includes = List.copyOf(
                Objects.requireNonNull(includes, "includes"));
        excludes = List.copyOf(excludes == null ? List.of() : excludes);
        tags = Set.copyOf(tags == null ? Set.of() : tags);
        if (includes.isEmpty()) {
            throw new IllegalArgumentException(
                    "region must contain at least one include box");
        }
    }

    public boolean contains(
            ResourceLocation queriedDimension,
            BlockPos position
    ) {
        if (!dimension.equals(queriedDimension)
                || position == null
                || includes.stream().noneMatch(box ->
                        box.contains(position))) {
            return false;
        }
        return excludes.stream().noneMatch(box -> box.contains(position));
    }

    public boolean hasTag(ResourceLocation tag) {
        return tags.contains(tag);
    }

    /** Inclusive block-coordinate box. A point has identical min and max. */
    public record Box(BlockPos min, BlockPos max) {
        public Box {
            min = Objects.requireNonNull(min, "min").immutable();
            max = Objects.requireNonNull(max, "max").immutable();
            if (max.getX() < min.getX()
                    || max.getY() < min.getY()
                    || max.getZ() < min.getZ()) {
                throw new IllegalArgumentException(
                        "region box max values must be >= min values");
            }
        }

        public boolean contains(BlockPos position) {
            return position.getX() >= min.getX()
                    && position.getX() <= max.getX()
                    && position.getY() >= min.getY()
                    && position.getY() <= max.getY()
                    && position.getZ() >= min.getZ()
                    && position.getZ() <= max.getZ();
        }
    }
}
