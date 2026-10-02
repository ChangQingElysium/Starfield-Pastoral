package com.stardew.craft.port;

/** The 1.21 normal-matrix trust bit, carried by Forge's PoseStack.Pose. */
public interface PortNormalPose {
    boolean stardewcraft$trustedNormals();
    void stardewcraft$trustedNormals(boolean trusted);
}
