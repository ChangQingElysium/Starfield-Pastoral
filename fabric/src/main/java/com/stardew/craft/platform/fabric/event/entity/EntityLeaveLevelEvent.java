package com.stardew.craft.platform.fabric.event.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/** Tracking ended after native cleanup. This includes unloading and does not imply death. */
public final class EntityLeaveLevelEvent extends EntityEvent {
    private final Level level;

    public EntityLeaveLevelEvent(Entity entity, Level level) {
        super(entity, false);
        this.level = level;
    }

    public Level getLevel() { return level; }
}
