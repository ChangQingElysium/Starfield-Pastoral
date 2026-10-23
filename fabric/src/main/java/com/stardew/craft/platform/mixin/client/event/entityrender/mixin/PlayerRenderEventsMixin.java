package com.stardew.craft.platform.mixin.client.event.entityrender.mixin;


import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.platform.fabric.client.event.entityrender.FabricEntityRenderEvents;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(PlayerRenderer.class)
public abstract class PlayerRenderEventsMixin {
    @WrapOperation(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    private void stardewcraft$player(PlayerRenderer renderer, LivingEntity entity, float yaw, float partialTick, PoseStack pose,
                                   MultiBufferSource buffers, int light, Operation<Void> original) {
        FabricEntityRenderEvents.player(FabricEntityRenderEvents.bus(), (AbstractClientPlayer) entity, (PlayerRenderer) (Object) this,
                partialTick, pose, buffers, light, () -> original.call(renderer, entity, yaw, partialTick, pose, buffers, light));
    }
}
