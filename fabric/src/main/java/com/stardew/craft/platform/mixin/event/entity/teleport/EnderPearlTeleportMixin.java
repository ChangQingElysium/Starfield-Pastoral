package com.stardew.craft.platform.mixin.event.entity.teleport;


import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.stardew.craft.platform.fabric.event.FabricEvents;
import com.stardew.craft.platform.fabric.event.entity.EntityTeleportEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(ThrownEnderpearl.class)
public abstract class EnderPearlTeleportMixin {
    @ModifyExpressionValue(method = "onHit", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;isAcceptingMessages()Z"))
    private boolean stardewcraft$pearl(boolean accepting, @Local ServerPlayer player, @Local(argsOnly = true) HitResult hit,
            @Share("stardewcraft$pearlTarget") LocalRef<EntityTeleportEvent.EnderPearl> decision) {
        if (!accepting) return false;
        var pearl = (ThrownEnderpearl) (Object) this;
        var event = FabricEvents.GAME.post(new EntityTeleportEvent.EnderPearl(player, pearl.getX(), pearl.getY(), pearl.getZ(), pearl, 5, hit));
        decision.set(event);
        return !event.isCanceled();
    }
    @ModifyExpressionValue(method = "onHit", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/projectile/ThrownEnderpearl;position()Lnet/minecraft/world/phys/Vec3;", ordinal = 0))
    private Vec3 stardewcraft$pearlTarget(Vec3 position,
            @Share("stardewcraft$pearlTarget") LocalRef<EntityTeleportEvent.EnderPearl> decision) {
        return decision.get().getTarget();
    }
    @ModifyArg(method = "onHit", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"), index = 1)
    private float stardewcraft$pearlDamage(float amount,
            @Share("stardewcraft$pearlTarget") LocalRef<EntityTeleportEvent.EnderPearl> decision) {
        return decision.get().getAttackDamage();
    }
}
