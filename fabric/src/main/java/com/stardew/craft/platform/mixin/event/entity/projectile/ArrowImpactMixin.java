package com.stardew.craft.platform.mixin.event.entity.projectile;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.stardew.craft.platform.fabric.event.entity.projectile.NativeProjectileImpacts;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AbstractArrow.class)
abstract class ArrowImpactMixin {
    @WrapOperation(method = "tick()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/projectile/AbstractArrow;hitTargetOrDeflectSelf(Lnet/minecraft/world/phys/HitResult;)Lnet/minecraft/world/entity/projectile/ProjectileDeflection;"), require = 1)
    private static ProjectileDeflection stardewcraft$impact(AbstractArrow arrow, HitResult result, Operation<ProjectileDeflection> original,
            @Local(index = 8) LocalRef<EntityHitResult> entityHit,
            @Share("stardewcraft$skipImpactImpulse") LocalBooleanRef skipImpulse) {
        skipImpulse.set(false);
        if (NativeProjectileImpacts.allowTarget(arrow, result)) return original.call(arrow, result);
        // Native's immediately following null test exits only the piercing loop. Tick movement,
        // gravity and block checks must continue; returning a fake deflection would change physics.
        entityHit.set(null);
        skipImpulse.set(true);
        return ProjectileDeflection.NONE;
    }

    @WrapOperation(method = "tick()V", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/projectile/AbstractArrow;hasImpulse:Z", opcode = Opcodes.PUTFIELD), require = 1)
    private static void stardewcraft$impactImpulse(AbstractArrow arrow, boolean value, Operation<Void> original,
            @Share("stardewcraft$skipImpactImpulse") LocalBooleanRef skipImpulse) {
        if (!skipImpulse.get()) original.call(arrow, value);
    }
}
