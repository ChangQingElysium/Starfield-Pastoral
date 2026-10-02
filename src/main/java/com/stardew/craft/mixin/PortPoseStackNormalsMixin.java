package com.stardew.craft.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.port.PortNormalPose;
import com.stardew.craft.port.PortVertex;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** PORT(1.20.1): the exact 1.21 scale matrices and copied/reset normal trust state. */
@Mixin(PoseStack.class)
public abstract class PortPoseStackNormalsMixin {
    @Shadow public abstract PoseStack.Pose last();
    @Unique private boolean stardewcraft$copiedNormalTrust;

    @Inject(method = "scale", at = @At("HEAD"), cancellable = true, require = 1)
    private void stardewcraft$scale(float x, float y, float z, CallbackInfo ci) {
        PortVertex.scale(last(), x, y, z);
        ci.cancel();
    }

    @Inject(method = "pushPose", at = @At("HEAD"), require = 1)
    private void stardewcraft$rememberTrust(CallbackInfo ci) {
        stardewcraft$copiedNormalTrust = ((PortNormalPose) (Object) last()).stardewcraft$trustedNormals();
    }

    @Inject(method = "pushPose", at = @At("RETURN"), require = 1)
    private void stardewcraft$copyTrust(CallbackInfo ci) {
        ((PortNormalPose) (Object) last()).stardewcraft$trustedNormals(stardewcraft$copiedNormalTrust);
    }

    @Inject(method = "setIdentity", at = @At("RETURN"), require = 1)
    private void stardewcraft$resetTrust(CallbackInfo ci) {
        ((PortNormalPose) (Object) last()).stardewcraft$trustedNormals(true);
    }
}
