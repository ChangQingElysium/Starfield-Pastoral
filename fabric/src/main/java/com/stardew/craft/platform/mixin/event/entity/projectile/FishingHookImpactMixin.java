package com.stardew.craft.platform.mixin.event.entity.projectile;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stardew.craft.platform.fabric.event.entity.projectile.NativeProjectileImpacts;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FishingHook.class)
abstract class FishingHookImpactMixin extends Projectile {
    protected FishingHookImpactMixin(EntityType<? extends Projectile> type, Level level) { super(type, level); }

    @WrapOperation(method = "checkCollision()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/projectile/FishingHook;hitTargetOrDeflectSelf(Lnet/minecraft/world/phys/HitResult;)Lnet/minecraft/world/entity/projectile/ProjectileDeflection;"), require = 1)
    private ProjectileDeflection stardewcraft$impact(FishingHook hook, HitResult result, Operation<ProjectileDeflection> original) {
        // The NeoForge patch deliberately calls onHit directly, bypassing entity deflection.
        if (NativeProjectileImpacts.allowDirectHit(hook, result)) this.onHit(result);
        return ProjectileDeflection.NONE; // Native checkCollision discards this return value.
    }
}
