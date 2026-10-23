package com.stardew.craft.platform.fabric.event.entity;

import net.minecraft.world.entity.Entity;

/** Runs around Entity.tick in tickNonPassenger on either side, as on the upstream loader. */
public abstract class EntityTickEvent extends EntityEvent {
    private EntityTickEvent(Entity entity, boolean cancelable) { super(entity, cancelable); }

    public static final class Pre extends EntityTickEvent {
        public Pre(Entity entity) { super(entity, true); }
    }

    public static final class Post extends EntityTickEvent {
        public Post(Entity entity) { super(entity, false); }
    }
}
