package com.stardew.craft.platform.fabric.event.entity.living;

import java.util.Collection;
import java.util.Objects;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;

public final class LivingDropsEvent extends LivingEvent {
    private final DamageSource source;
    private final Collection<ItemEntity> drops;
    private final boolean recentlyHit;
    public LivingDropsEvent(LivingEntity entity, DamageSource source, Collection<ItemEntity> drops, boolean recentlyHit) {
        super(entity, true);
        this.source = Objects.requireNonNull(source);
        this.drops = Objects.requireNonNull(drops);
        this.recentlyHit = recentlyHit;
    }
    public DamageSource getSource() { return source; }
    public Collection<ItemEntity> getDrops() { return drops; }
    public boolean isRecentlyHit() { return recentlyHit; }
}
