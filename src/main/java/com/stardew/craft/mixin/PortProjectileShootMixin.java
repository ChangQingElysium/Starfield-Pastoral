package com.stardew.craft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stardew.craft.port.PortInheritance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * PORT(1.20.1): two 1.21.1 {@code Projectile} defaults the mod's projectiles inherit.
 * <ul>
 * <li>{@code shootFromRotation} adds the shooter's {@code getKnownMovement()} (for a server player: the last movement
 * the client reported) instead of its server-side {@code getDeltaMovement()} (≈ 0 / gravity for a server player).
 * The mod launches its weapon-skill projectiles and the fishing cast (a vanilla {@code FishingHook}, which vanilla
 * itself never launches through {@code shootFromRotation}) this way, so a moving player's throw carried no momentum
 * on 1.20.1.</li>
 * <li>{@code shoot} sets {@code hasImpulse}, so the new velocity is sent to clients on the next tracker update.</li>
 * </ul>
 * Scope: StardewCraft projectiles and {@code FishingHook} ({@link PortInheritance#usesModProjectileRules}).
 */
@Mixin(Projectile.class)
public abstract class PortProjectileShootMixin {
    @WrapOperation(method = "shootFromRotation", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/world/entity/Entity;getDeltaMovement()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 stardewcraft$shooterKnownMovement(Entity shooter, Operation<Vec3> original) {
        if (PortInheritance.usesModProjectileRules((Projectile) (Object) this)) {
            return PortInheritance.shooterMovement(shooter);
        }
        return original.call(shooter);
    }

    @Inject(method = "shoot", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/world/entity/projectile/Projectile;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V"))
    private void stardewcraft$shootHasImpulse(double x, double y, double z, float velocity, float inaccuracy, CallbackInfo ci) {
        Projectile self = (Projectile) (Object) this;
        if (PortInheritance.usesModProjectileRules(self)) self.hasImpulse = true;
    }
}
