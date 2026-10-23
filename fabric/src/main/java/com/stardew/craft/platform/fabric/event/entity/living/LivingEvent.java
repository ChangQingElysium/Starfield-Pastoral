package com.stardew.craft.platform.fabric.event.entity.living;

import com.stardew.craft.platform.fabric.event.entity.EntityEvent;
import net.minecraft.world.entity.LivingEntity;

public abstract class LivingEvent extends EntityEvent {
    protected LivingEvent(LivingEntity entity, boolean cancelable) { super(entity, cancelable); }
    protected LivingEvent(LivingEntity entity, boolean cancelable, boolean allowNullEntity) {
        super(entity, cancelable, allowNullEntity);
    }

    @Override public LivingEntity getEntity() { return (LivingEntity) super.getEntity(); }
}
