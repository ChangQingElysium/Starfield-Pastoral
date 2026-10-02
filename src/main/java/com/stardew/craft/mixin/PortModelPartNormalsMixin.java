package com.stardew.craft.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.stardew.craft.port.PortVertex;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** PORT(1.20.1): ModelPart.Cube uses Pose.transformNormal in 1.21. */
@Mixin(ModelPart.Cube.class)
public abstract class PortModelPartNormalsMixin {
    @Redirect(method = "compile", at = @At(value = "INVOKE",
            target = "Lorg/joml/Matrix3f;transform(Lorg/joml/Vector3f;)Lorg/joml/Vector3f;", remap = false),
            require = 1, expect = 1, allow = 1)
    private Vector3f stardewcraft$normal(Matrix3f matrix, Vector3f normal, PoseStack.Pose pose,
            VertexConsumer consumer, int light, int overlay, float red, float green, float blue, float alpha) {
        return PortVertex.transformNormal(pose, normal, normal);
    }
}
