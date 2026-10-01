package com.stardew.craft.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Supported third-person collapse pose for combat HP=0. */
@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererCombatCollapseMixin extends net.minecraft.client.renderer.entity.LivingEntityRenderer<AbstractClientPlayer, net.minecraft.client.model.PlayerModel<AbstractClientPlayer>> {
    protected PlayerRendererCombatCollapseMixin(net.minecraft.client.renderer.entity.EntityRendererProvider.Context context,
            net.minecraft.client.model.PlayerModel<AbstractClientPlayer> model, float shadow) { super(context,model,shadow); }
    @org.spongepowered.asm.mixin.Unique private net.minecraft.client.model.PlayerModel<AbstractClientPlayer> stardewcraft$normalModel;
    @org.spongepowered.asm.mixin.Unique private com.stardew.craft.client.combat.CollapsePlayerModel<AbstractClientPlayer> stardewcraft$collapseModel;

    @Inject(method="render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",at=@At("HEAD"))
    private void stardewcraft$selectCollapseModel(AbstractClientPlayer player,float yaw,float partialTick,PoseStack stack,
            net.minecraft.client.renderer.MultiBufferSource buffer,int light,CallbackInfo ci) {
        if (!com.stardew.craft.client.combat.CombatCollapseClientState.isCollapsing(player)) return;
        if (stardewcraft$collapseModel == null)
            stardewcraft$collapseModel = new com.stardew.craft.client.combat.CollapsePlayerModel<>(
                    com.stardew.craft.port.net.minecraft.client.resources.PlayerSkin.of(player).model() == com.stardew.craft.port.net.minecraft.client.resources.PlayerSkin.Model.SLIM);
        stardewcraft$normalModel = model;
        model = stardewcraft$collapseModel;
    }
    @Inject(method="render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",at=@At("RETURN"))
    private void stardewcraft$restoreNormalModel(AbstractClientPlayer player,float yaw,float partialTick,PoseStack stack,
            net.minecraft.client.renderer.MultiBufferSource buffer,int light,CallbackInfo ci) {
        if (stardewcraft$normalModel == null) return;
        model = stardewcraft$normalModel;
        stardewcraft$normalModel = null;
    }


    @Inject(method="getRenderOffset(Lnet/minecraft/client/player/AbstractClientPlayer;F)Lnet/minecraft/world/phys/Vec3;",at=@At("HEAD"),cancellable=true)
    private void stardewcraft$collapseOffset(AbstractClientPlayer player,float partialTick,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<net.minecraft.world.phys.Vec3> cir) {
        if(com.stardew.craft.client.combat.CombatCollapseClientState.isCollapsing(player))
            cir.setReturnValue(net.minecraft.world.phys.Vec3.ZERO);
    }

    @Inject(
        method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V",
        at = @At("HEAD"), cancellable = true
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
        var frame = com.stardew.craft.client.combat.CombatCollapseModelPose.frame(player, partialTick);
        if (frame == null) return;
        // Knockout owns the root pose even if the hit interrupted swimming/flying.
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0F - yBodyRot));
        var model = ((PlayerRenderer) (Object) this).getModel();
        com.stardew.craft.client.combat.CombatCollapseModelPose.root(poseStack, model, frame, .9375F, player);
        ci.cancel();
    }
}
