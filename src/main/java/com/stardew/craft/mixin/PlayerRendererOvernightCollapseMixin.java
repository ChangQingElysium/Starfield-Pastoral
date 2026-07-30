package com.stardew.craft.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.stardew.craft.network.overnight.OvernightCollapseClientState;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Rotates the local player's third-person body into a prone pose during overnight collapse.
 */
@Mixin(PlayerRenderer.class)
public class PlayerRendererOvernightCollapseMixin {
    private static final float PIVOT_Y = 0.45F;

    @Inject(
        method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V",
        at = @At("TAIL")
    )
    private void stardewcraft$applyOvernightCollapse(
            AbstractClientPlayer player,
            PoseStack poseStack,
            float bob,
            float yBodyRot,
            float partialTick,
            float scale,
            CallbackInfo ci
    ) {
        float degrees = OvernightCollapseClientState.collapseRotationDegrees(player, partialTick);
        if (degrees <= 0.0F) {
            return;
        }
        poseStack.rotateAround(Axis.ZP.rotationDegrees(degrees), 0.0F, PIVOT_Y / scale, 0.0F);
    }
}
