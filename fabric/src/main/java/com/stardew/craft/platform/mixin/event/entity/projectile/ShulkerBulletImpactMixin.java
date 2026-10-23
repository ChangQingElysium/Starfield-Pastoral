package com.stardew.craft.platform.mixin.event.entity.projectile;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stardew.craft.platform.fabric.event.entity.projectile.NativeProjectileImpacts;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ShulkerBullet.class)
abstract class ShulkerBulletImpactMixin {
    @WrapOperation(method = "tick()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/projectile/ShulkerBullet;hitTargetOrDeflectSelf(Lnet/minecraft/world/phys/HitResult;)Lnet/minecraft/world/entity/projectile/ProjectileDeflection;"), require = 1)
    private static ProjectileDeflection stardewcraft$impact(ShulkerBullet projectile, HitResult result, Operation<ProjectileDeflection> original) {
        return NativeProjectileImpacts.hitTarget(projectile, result, original);
    }
}
