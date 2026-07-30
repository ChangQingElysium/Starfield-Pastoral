package com.stardew.craft.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.stardew.craft.client.combat.CombatCollapseClientState;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Third-person prone pose and initial Farmer-style jitter for combat HP=0. */
@Mixin(PlayerRenderer.class)
public class PlayerRendererCombatCollapseMixin {
    private static final float PIVOT_Y = 0.45F;

    @Inject(
        method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V",
        at = @At("TAIL")
    )
    private void stardewcraft$applyCombatCollapse(
            AbstractClientPlayer player,
            PoseStack poseStack,
            float bob,
            float yBodyRot,
            float partialTick,
            float scale,
            CallbackInfo ci
    ) {
        float degrees = CombatCollapseClientState.bodyRotationDegrees(player, partialTick);
        if (degrees <= 0.0F) {
            return;
        }
        poseStack.translate(
            CombatCollapseClientState.bodyJitterX(player, partialTick),
            0.0F,
            CombatCollapseClientState.bodyJitterZ(player, partialTick)
        );
        poseStack.rotateAround(Axis.ZP.rotationDegrees(degrees), 0.0F, PIVOT_Y / scale, 0.0F);
    }
}
