package com.stardew.craft.platform.fabric.event.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/** Posted before insertion, including disk loads; cancellation leaves the native storage unchanged. */
public final class EntityJoinLevelEvent extends EntityEvent {
    private final Level level;
    private final boolean loadedFromDisk;

    public EntityJoinLevelEvent(Entity entity, Level level) { this(entity, level, false); }

    public EntityJoinLevelEvent(Entity entity, Level level, boolean loadedFromDisk) {
        super(entity, true);
        this.level = level;
        this.loadedFromDisk = loadedFromDisk;
    }

    public Level getLevel() { return level; }
    public boolean loadedFromDisk() { return loadedFromDisk; }
}
