package com.stardew.craft.platform.mixin.client.event.entityrender.mixin;


import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.platform.fabric.client.event.entityrender.FabricEntityRenderEvents;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;

@Environment(EnvType.CLIENT)
@Mixin(LivingEntityRenderer.class)
public abstract class LivingRenderEventsMixin<T extends LivingEntity, M extends EntityModel<T>> {
    @SuppressWarnings("unchecked")
    @WrapMethod(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V")
    private void stardewcraft$living(T entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, Operation<Void> original) {
        FabricEntityRenderEvents.living(FabricEntityRenderEvents.bus(), entity, (LivingEntityRenderer<T, M>) (Object) this,
                partialTick, pose, buffers, light, () -> original.call(entity, yaw, partialTick, pose, buffers, light));
    }
}
