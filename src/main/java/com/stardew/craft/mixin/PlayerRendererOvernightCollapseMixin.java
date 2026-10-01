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
        // PORT(1.20.1): no trailing entity-scale parameter before 1.20.5
        method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFF)V",
        at = @At("TAIL")
    )
    private void stardewcraft$applyOvernightCollapse(
            AbstractClientPlayer player,
            PoseStack poseStack,
            float bob,
            float yBodyRot,
            float partialTick,
            CallbackInfo ci
    ) {
        float scale = player.getScale(); // PORT(1.20.1): 1.21 passes LivingEntity#getScale() (no SCALE attribute on players here)
        float degrees = OvernightCollapseClientState.collapseRotationDegrees(player, partialTick);
        if (degrees <= 0.0F) {
            return;
        }
        poseStack.rotateAround(Axis.XP.rotationDegrees(degrees), 0.0F, PIVOT_Y / scale, 0.0F);
    }
}
