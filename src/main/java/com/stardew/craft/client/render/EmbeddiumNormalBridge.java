package com.stardew.craft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.port.PortNormalPose;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;

/** PORT(1.20.1): Sodium 0.6.13's trusted-normal math for Embeddium's immediate writers. */
public final class EmbeddiumNormalBridge {
    private EmbeddiumNormalBridge() {}

    public static int transformPacked(PoseStack.Pose pose, int packed) {
        Matrix3f matrix = pose.normal();
        float x = (byte) packed * (1.0F / 127.0F);
        float y = (byte) (packed >> 8) * (1.0F / 127.0F);
        float z = (byte) (packed >> 16) * (1.0F / 127.0F);
        // Keep Sodium's operation order, including its signed-byte truncation on repack.
        return packTransformed(pose,
                matrix.m00() * x + (matrix.m10() * y + matrix.m20() * z),
                matrix.m01() * x + (matrix.m11() * y + matrix.m21() * z),
                matrix.m02() * x + (matrix.m12() * y + matrix.m22() * z));
    }

    public static int transformDirection(PoseStack.Pose pose, Direction direction) {
        Matrix3f matrix = pose.normal();
        // Axis selection, not multiplication by zero: this also matches singular-matrix behavior.
        return switch (direction) {
            case DOWN -> packTransformed(pose, -matrix.m10, -matrix.m11, -matrix.m12);
            case UP -> packTransformed(pose, matrix.m10, matrix.m11, matrix.m12);
            case NORTH -> packTransformed(pose, -matrix.m20, -matrix.m21, -matrix.m22);
            case SOUTH -> packTransformed(pose, matrix.m20, matrix.m21, matrix.m22);
            case WEST -> packTransformed(pose, -matrix.m00, -matrix.m01, -matrix.m02);
            case EAST -> packTransformed(pose, matrix.m00, matrix.m01, matrix.m02);
        };
    }

    private static int packTransformed(PoseStack.Pose pose, float x, float y, float z) {
        if (!((PortNormalPose) (Object) pose).stardewcraft$trustedNormals()) {
            float scalar = org.joml.Math.invsqrt(org.joml.Math.fma(x, x, org.joml.Math.fma(y, y, z * z)));
            x *= scalar;
            y *= scalar;
            z *= scalar;
        }
        return encode(x) | encode(y) << 8 | encode(z) << 16;
    }

    private static int encode(float component) {
        return (int) (Mth.clamp(component, -1.0F, 1.0F) * 127.0F) & 255;
    }
}
