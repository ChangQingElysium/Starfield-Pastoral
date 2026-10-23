package com.stardew.craft.platform.fabric.event.entity.projectile;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.stardew.craft.platform.fabric.event.entity.ProjectileImpacts;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.phys.HitResult;

/** The two distinct impact gates in NeoForge 21.1.217's native projectile patches. */
public final class NativeProjectileImpacts {
    private NativeProjectileImpacts() {}

    /** Arrows, thrown/hurting projectiles, shulker bullets and llama spit ignore misses. */
    public static boolean allowTarget(Projectile projectile, HitResult result) {
        return result.getType() != HitResult.Type.MISS && !ProjectileImpacts.onProjectileImpact(projectile, result);
    }

    /** Fishing hooks and firework onHit still delegate a miss, without publishing an event. */
    public static boolean allowDirectHit(Projectile projectile, HitResult result) {
        return result.getType() == HitResult.Type.MISS || !ProjectileImpacts.onProjectileImpact(projectile, result);
    }

    public static ProjectileDeflection hitTarget(Projectile projectile, HitResult result, Operation<ProjectileDeflection> original) {
        return allowTarget(projectile, result) ? original.call(projectile, result) : ProjectileDeflection.NONE;
    }
}
