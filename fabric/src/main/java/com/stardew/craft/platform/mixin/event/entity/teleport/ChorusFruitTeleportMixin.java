package com.stardew.craft.platform.mixin.event.entity.teleport;


import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.stardew.craft.platform.fabric.event.FabricEvents;
import com.stardew.craft.platform.fabric.event.entity.EntityTeleportEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ChorusFruitItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(ChorusFruitItem.class)
public abstract class ChorusFruitTeleportMixin {
    @Inject(method = "finishUsingItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;randomTeleport(DDDZ)Z"), cancellable = true)
    private void stardewcraft$chorus(CallbackInfoReturnable<ItemStack> callback, @Local(argsOnly = true) LivingEntity entity,
            @Local(ordinal = 0) double x, @Local(ordinal = 1) double y, @Local(ordinal = 2) double z,
            @Local(ordinal = 1) ItemStack result, @Share("stardewcraft$chorusTarget") LocalRef<EntityTeleportEvent.ChorusFruit> decision) {
        var event = FabricEvents.GAME.post(new EntityTeleportEvent.ChorusFruit(entity, x, y, z));
        decision.set(event);
        // Neo returns the already-consumed result here, skipping all later attempts and the cooldown.
        if (event.isCanceled()) callback.setReturnValue(result);
    }
    @ModifyArgs(method = "finishUsingItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;randomTeleport(DDDZ)Z"))
    private void stardewcraft$chorusTarget(Args args, @Share("stardewcraft$chorusTarget") LocalRef<EntityTeleportEvent.ChorusFruit> decision) {
        var event = decision.get(); args.set(0, event.getTargetX()); args.set(1, event.getTargetY()); args.set(2, event.getTargetZ());
    }
}
