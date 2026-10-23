package com.stardew.craft.platform.mixin.client.extension.mixin;


import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.platform.fabric.client.extension.IClientItemExtensions;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(ItemRenderer.class)
public abstract class ItemCustomRendererMixin {
    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/BlockEntityWithoutLevelRenderer;renderByItem(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V"))
    private void stardewcraft$customRenderer(BlockEntityWithoutLevelRenderer vanilla, ItemStack stack, ItemDisplayContext display,
                                           PoseStack pose, MultiBufferSource buffers, int light, int overlay, Operation<Void> original) {
        var extension = IClientItemExtensions.of(stack);
        var renderer = extension == IClientItemExtensions.DEFAULT ? vanilla : extension.getCustomRenderer();
        original.call(renderer, stack, display, pose, buffers, light, overlay);
    }
}
