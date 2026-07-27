package com.stardew.craft.api.v1.festival;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;

/**
 * Namespaced festival map overlay whose placement is anchored by the shared
 * world-anchor catalog.
 */
public record StardewFestivalMapOverlay(
        ResourceLocation id,
        ResourceLocation locationId,
        ResourceLocation originAnchor,
        @Nullable ResourceLocation baseSchematic,
        ResourceLocation festivalSchematic,
        BlockPos boundsMinOffset,
        BlockPos boundsMaxOffset,
        List<BlockPos> safePositionOffsets,
        boolean requiresBlackFade,
        boolean cleanupDroppedItems,
        boolean cleanupTaggedEntities,
        TreeClearance treeClearance
) {
    public StardewFestivalMapOverlay {
        id = Objects.requireNonNull(id, "id");
        locationId = Objects.requireNonNull(locationId, "locationId");
        originAnchor = Objects.requireNonNull(
                originAnchor, "originAnchor");
        festivalSchematic = Objects.requireNonNull(
                festivalSchematic, "festivalSchematic");
        boundsMinOffset = Objects.requireNonNull(
                boundsMinOffset, "boundsMinOffset").immutable();
        boundsMaxOffset = Objects.requireNonNull(
                boundsMaxOffset, "boundsMaxOffset").immutable();
        if (boundsMaxOffset.getX() < boundsMinOffset.getX()
                || boundsMaxOffset.getY() < boundsMinOffset.getY()
                || boundsMaxOffset.getZ() < boundsMinOffset.getZ()) {
            throw new IllegalArgumentException(
                    "overlay max bounds must be >= min bounds");
        }
        safePositionOffsets = List.copyOf(
                safePositionOffsets == null
                        ? List.of()
                        : safePositionOffsets.stream()
                                .map(BlockPos::immutable)
                                .toList());
        treeClearance = treeClearance == null
                ? TreeClearance.NONE : treeClearance;
    }

    /** A null base schematic means capture and restore the live map. */
    public boolean usesRuntimeBase() {
        return baseSchematic == null;
    }

    public record TreeClearance(
            int horizontalRadius,
            int up,
            int down
    ) {
        public static final TreeClearance NONE =
                new TreeClearance(0, 0, 0);

        public TreeClearance {
            if (horizontalRadius < 0 || up < 0 || down < 0) {
                throw new IllegalArgumentException(
                        "tree-clearance values must be non-negative");
            }
        }
    }
}
