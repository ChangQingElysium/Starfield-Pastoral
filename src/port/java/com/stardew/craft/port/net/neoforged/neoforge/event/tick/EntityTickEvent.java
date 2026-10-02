package com.stardew.craft.port.net.neoforged.neoforge.event.tick;

import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.eventbus.api.Cancelable;

/**
 * PORT(1.20.1): NeoForge 21.1 entity tick event. Forge 1.20.1 has no per-entity tick event, so it is fired by
 * {@code com.stardew.craft.mixin.PortServerLevelEntityTickMixin} / {@code PortClientLevelEntityTickMixin} around
 * {@code Entity#tick()} (non-passengers) and {@code Entity#rideTick()} (passengers) in both level types, exactly where
 * NeoForge fires it. Cancelling {@link Pre} skips that entity's tick and its {@link Post}.
 */
public abstract class EntityTickEvent extends EntityEvent {
    protected EntityTickEvent(Entity entity) {
        super(entity);
    }

    @Cancelable
    public static class Pre extends EntityTickEvent {
        public Pre() {
            this(null);
        }

        public Pre(Entity entity) {
            super(entity);
        }
    }

    public static class Post extends EntityTickEvent {
        public Post() {
            this(null);
        }

        public Post(Entity entity) {
            super(entity);
        }
    }
}
