package com.stardew.craft.api.v1.farm;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Set;

/**
 * A stable, farm-relative point that other systems may reference by ID.
 *
 * <p>Tags describe capabilities such as an NPC stand point or festival entry;
 * they do not transfer authority to the client.
 */
public record StardewFarmLayoutAttachment(
        ResourceLocation id,
        BlockPos offset,
        float yaw,
        Set<ResourceLocation> tags
) {
    public StardewFarmLayoutAttachment {
        id = Objects.requireNonNull(id, "id");
        offset = Objects.requireNonNull(offset, "offset").immutable();
        tags = Set.copyOf(Objects.requireNonNull(tags, "tags"));
        if (tags.size() > 32) {
            throw new IllegalArgumentException(
                    "Farm layout attachment has too many tags: " + id);
        }
    }

    public StardewFarmLayoutAttachment(
            ResourceLocation id,
            BlockPos offset,
            float yaw
    ) {
        this(id, offset, yaw, Set.of());
    }

    public BlockPos resolve(BlockPos farmOrigin) {
        return Objects.requireNonNull(farmOrigin, "farmOrigin")
                .offset(offset);
    }
}
