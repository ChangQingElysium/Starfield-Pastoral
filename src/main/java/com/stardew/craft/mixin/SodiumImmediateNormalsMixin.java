package com.stardew.craft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.client.render.EmbeddiumNormalBridge;
import com.stardew.craft.client.render.EmbeddiumQuadAccess;
import org.joml.Matrix3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

/** Embeddium bypasses VertexConsumer's bulk writer; retain Sodium 0.6.13's accurate normals. */
@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.immediate.model.BakedModelEncoder", remap = false)
public abstract class SodiumImmediateNormalsMixin {
    @WrapOperation(method = {
            "writeQuadVertices(Lnet/caffeinemc/mods/sodium/api/vertex/buffer/VertexBufferWriter;Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lme/jellysquid/mods/sodium/client/model/quad/ModelQuadView;III)V",
            "writeQuadVertices(Lnet/caffeinemc/mods/sodium/api/vertex/buffer/VertexBufferWriter;Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lme/jellysquid/mods/sodium/client/model/quad/ModelQuadView;FFFF[FZ[II)V"
    }, at = @At(value = "INVOKE", target = "Lme/jellysquid/mods/sodium/client/render/immediate/model/BakedModelEncoder;mergeNormalAndMult(IILorg/joml/Matrix3f;)I"),
            require = 2, expect = 2, allow = 2)
    private static int stardewcraft$accurateNormal(int packed, int calculated, Matrix3f matrix,
            Operation<Integer> original, @Coerce Object writer, PoseStack.Pose pose, @Coerce Object quad) {
        int accurate = packed != 0 ? packed : ((EmbeddiumQuadAccess.Normal) quad).stardewcraft$getComputedFaceNormal();
        return EmbeddiumNormalBridge.transformPacked(pose, accurate);
    }
}
