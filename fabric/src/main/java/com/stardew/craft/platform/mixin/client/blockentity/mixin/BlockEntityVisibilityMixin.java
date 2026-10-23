package com.stardew.craft.platform.mixin.client.blockentity.mixin;


import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.platform.fabric.client.blockentity.BlockEntityVisibility;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelRenderer.class)
abstract class BlockEntityVisibilityMixin {
    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderDispatcher;render(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V"), require = 2)
    private void stardewcraft$rendererBounds(BlockEntityRenderDispatcher dispatcher, BlockEntity entity, float partialTick,
            PoseStack pose, MultiBufferSource buffers, Operation<Void> original, @Local Frustum frustum) {
        // Covers both the visible section list and off-screen/global list; dispatcher distance/state checks still run.
        if (BlockEntityVisibility.isVisible(dispatcher, entity, frustum)) original.call(dispatcher, entity, partialTick, pose, buffers);
    }
}
