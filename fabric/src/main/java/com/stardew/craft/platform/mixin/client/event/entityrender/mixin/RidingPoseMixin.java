package com.stardew.craft.platform.mixin.client.event.entityrender.mixin;


import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.stardew.craft.platform.fabric.event.sleep.riding.RiderPoses;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(LivingEntityRenderer.class)
public abstract class RidingPoseMixin {
    @WrapOperation(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isPassenger()Z"), require = 3)
    private boolean stardewcraft$shouldSit(LivingEntity entity, Operation<Boolean> original,
            @Share("stardewcraft$ridingPose") LocalRef<Boolean> cached) {
        // Neo computes shouldSit once and reuses it for model.riding, mounted head/body yaw,
        // and the walk-animation branch. Returning a cached value covers all three native sites.
        if (cached.get() == null) {
            boolean passenger = original.call(entity);
            cached.set(passenger && RiderPoses.shouldSit(true, entity.getVehicle()));
        }
        return cached.get();
    }
}
