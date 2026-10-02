package com.stardew.craft.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.port.PortNormalPose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(PoseStack.Pose.class)
public abstract class PortPoseNormalTrustMixin implements PortNormalPose {
    @Unique private boolean stardewcraft$trustedNormals = true;

    @Override public boolean stardewcraft$trustedNormals() { return stardewcraft$trustedNormals; }
    @Override public void stardewcraft$trustedNormals(boolean trusted) { stardewcraft$trustedNormals = trusted; }
}
