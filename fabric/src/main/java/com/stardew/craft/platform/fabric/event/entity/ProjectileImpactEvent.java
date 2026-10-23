package com.stardew.craft.platform.fabric.event.entity;

import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.HitResult;

public final class ProjectileImpactEvent extends EntityEvent {
    private final HitResult result;
    public ProjectileImpactEvent(Projectile projectile, HitResult result) {
        super(projectile, true);
        this.result = java.util.Objects.requireNonNull(result);
    }
    public HitResult getRayTraceResult() { return result; }
    public Projectile getProjectile() { return (Projectile) getEntity(); }
}
