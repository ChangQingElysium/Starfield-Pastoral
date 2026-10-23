package com.stardew.craft.platform.fabric.event.entity;

import com.stardew.craft.platform.fabric.event.FabricEvents;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.HitResult;

public final class ProjectileImpacts {
    private ProjectileImpacts() {}
    public static boolean onProjectileImpact(Projectile projectile, HitResult result) {
        return FabricEvents.GAME.post(new ProjectileImpactEvent(projectile, result)).isCanceled();
    }
}
