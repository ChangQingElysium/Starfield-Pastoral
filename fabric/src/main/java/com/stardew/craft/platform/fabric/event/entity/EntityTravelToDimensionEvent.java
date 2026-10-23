package com.stardew.craft.platform.fabric.event.entity;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

public final class EntityTravelToDimensionEvent extends EntityEvent {
    private final ResourceKey<Level> dimension;
    public EntityTravelToDimensionEvent(Entity entity, ResourceKey<Level> dimension) {
        super(entity, true);
        this.dimension = java.util.Objects.requireNonNull(dimension);
    }
    public ResourceKey<Level> getDimension() { return dimension; }
}
