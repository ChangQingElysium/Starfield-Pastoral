package com.stardew.craft.api.v1.mining;

import net.minecraft.world.entity.EntityType;

import javax.annotation.Nullable;

@FunctionalInterface
public interface StardewMineMonsterProvider {
    /** Return an entity type to handle this spawn, or {@code null} to continue to the next provider. */
    @Nullable EntityType<?> select(StardewMineMonsterContext context);
}
