package com.stardew.craft.platform.fabric.event.level;

import com.stardew.craft.platform.fabric.event.Event;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;

public abstract class ExplosionEvent extends Event {
    private final Level level;
    private final Explosion explosion;
    protected ExplosionEvent(Level level, Explosion explosion) {
        this.level = java.util.Objects.requireNonNull(level);
        this.explosion = java.util.Objects.requireNonNull(explosion);
    }
    public Level getLevel() { return level; }
    public Explosion getExplosion() { return explosion; }
    public static final class Detonate extends ExplosionEvent {
        private final List<Entity> entities;
        public Detonate(Level level, Explosion explosion, List<Entity> entities) {
            super(level, explosion);
            this.entities = java.util.Objects.requireNonNull(entities);
        }
        public List<BlockPos> getAffectedBlocks() { return getExplosion().getToBlow(); }
        public List<Entity> getAffectedEntities() { return entities; }
    }
}
