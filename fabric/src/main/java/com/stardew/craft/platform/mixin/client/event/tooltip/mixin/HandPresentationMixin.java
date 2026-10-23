package com.stardew.craft.platform.mixin.client.event.tooltip.mixin;


import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.platform.fabric.client.event.RenderHandEvent;
import com.stardew.craft.platform.fabric.client.event.tooltip.FabricPresentationEvents;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(ItemInHandRenderer.class)
public abstract class HandPresentationMixin {
    @WrapOperation(method = "renderHandsWithItems", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderArmWithItem(Lnet/minecraft/client/player/AbstractClientPlayer;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    private void stardewcraft$hand(ItemInHandRenderer renderer, AbstractClientPlayer player, float partialTick, float pitch,
                                  InteractionHand hand, float swing, ItemStack stack, float equip, PoseStack pose,
                                  MultiBufferSource buffers, int light, Operation<Void> original) {
        // Runs after vanilla hand selection and interpolation, independently for each selected hand.
        FabricPresentationEvents.hand(new RenderHandEvent(hand, pose, buffers, light, partialTick, pitch, swing, equip, stack),
                () -> original.call(renderer, player, partialTick, pitch, hand, swing, stack, equip, pose, buffers, light));
    }
}
