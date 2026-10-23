package com.stardew.craft.platform.mixin.event.entity.teleport;


import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.stardew.craft.platform.fabric.event.FabricEvents;
import com.stardew.craft.platform.fabric.event.entity.EntityTeleportEvent;
import net.minecraft.world.entity.monster.EnderMan;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(EnderMan.class)
public abstract class EndermanTeleportMixin {
    @Inject(method = "teleport(DDD)Z", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/monster/EnderMan;position()Lnet/minecraft/world/phys/Vec3;"), cancellable = true)
    private void stardewcraft$ender(CallbackInfoReturnable<Boolean> callback,
            @Local(argsOnly = true, ordinal = 0) double x, @Local(argsOnly = true, ordinal = 1) double y,
            @Local(argsOnly = true, ordinal = 2) double z, @Share("stardewcraft$enderTarget") LocalRef<EntityTeleportEvent.EnderEntity> decision) {
        var event = FabricEvents.GAME.post(new EntityTeleportEvent.EnderEntity((EnderMan) (Object) this, x, y, z));
        decision.set(event);
        if (event.isCanceled()) callback.setReturnValue(false);
    }
    @ModifyArgs(method = "teleport(DDD)Z", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/monster/EnderMan;randomTeleport(DDDZ)Z"))
    private void stardewcraft$enderTarget(Args args, @Share("stardewcraft$enderTarget") LocalRef<EntityTeleportEvent.EnderEntity> decision) {
        var event = decision.get(); args.set(0, event.getTargetX()); args.set(1, event.getTargetY()); args.set(2, event.getTargetZ());
    }
}
