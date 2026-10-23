package com.stardew.craft.platform.fabric.event.entity;

import com.stardew.craft.platform.fabric.event.Event;
import java.util.Objects;
import net.minecraft.world.entity.Entity;

public abstract class EntityEvent extends Event {
    private final Entity entity;

    protected EntityEvent(Entity entity, boolean cancelable) {
        this(entity, cancelable, false);
    }

    /** Specific inventory/search tooltip events have no player during bootstrap. */
    protected EntityEvent(Entity entity, boolean cancelable, boolean allowNullEntity) {
        super(cancelable);
        this.entity = allowNullEntity ? entity : Objects.requireNonNull(entity, "entity");
    }

    public Entity getEntity() { return entity; }
}
