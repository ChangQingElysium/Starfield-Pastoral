package com.stardew.craft.platform.fabric.event.entity.living;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** The native death operation has not applied loot, messages, score, or the dead flag. */
public final class LivingDeathEvent extends LivingEvent {
    private final DamageSource source;

    public LivingDeathEvent(LivingEntity entity, DamageSource source) {
        super(entity, true);
        this.source = source;
    }

    public DamageSource getSource() { return source; }
}
