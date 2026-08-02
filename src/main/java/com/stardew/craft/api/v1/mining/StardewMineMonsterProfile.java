package com.stardew.craft.api.v1.mining;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

import java.util.Objects;
import java.util.Set;

/** Stable identity and routing metadata for one mine-monster profile. */
public record StardewMineMonsterProfile(
        ResourceLocation id,
        EntityType<? extends Mob> entityType,
        String translationKey,
        Set<String> progressTags
) {
    public StardewMineMonsterProfile {
        id = Objects.requireNonNull(id, "id");
        entityType = Objects.requireNonNull(entityType, "entityType");
        translationKey = Objects.requireNonNull(
                translationKey, "translationKey").trim();
        if (translationKey.isEmpty()) {
            throw new IllegalArgumentException(
                    "translationKey must not be blank");
        }
        progressTags = Set.copyOf(
                Objects.requireNonNull(progressTags, "progressTags"));
        if (progressTags.stream().anyMatch(tag ->
                tag == null || tag.isBlank())) {
            throw new IllegalArgumentException(
                    "progressTags must not contain blank values");
        }
    }

    /** Source-compatible default for profiles that use their entity type name. */
    public StardewMineMonsterProfile(
            ResourceLocation id,
            EntityType<? extends Mob> entityType,
            Set<String> progressTags
    ) {
        this(id, entityType, entityType.getDescriptionId(), progressTags);
    }
}
