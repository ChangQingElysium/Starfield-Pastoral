package com.stardew.craft.platform.mixin.client.extension.mixin;


import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.platform.fabric.client.extension.IClientItemExtensions;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ItemInHandRenderer.class)
public abstract class ItemHandTransformMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow public abstract void renderItem(LivingEntity entity, ItemStack stack, ItemDisplayContext display, boolean left,
                                          PoseStack pose, MultiBufferSource buffers, int light);
    @Inject(method = "renderArmWithItem", at = @At(value = "INVOKE", ordinal = 1,
            target = "Lnet/minecraft/client/player/AbstractClientPlayer;isUsingItem()Z"), cancellable = true)
    private void stardewcraft$handTransform(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand,
                                          float swing, ItemStack stack, float equip, PoseStack pose, MultiBufferSource buffers,
                                          int light, CallbackInfo callback, @Local HumanoidArm arm) {
        if (IClientItemExtensions.of(stack).applyForgeHandTransform(pose, minecraft.player, arm, stack, partialTick, equip, swing)) {
            boolean right = arm == HumanoidArm.RIGHT;
            renderItem(player, stack, right ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
                    !right, pose, buffers, light);
            pose.popPose();
            callback.cancel();
        }
    }
}
