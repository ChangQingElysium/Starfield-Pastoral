package com.stardew.craft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.client.render.EmbeddiumNormalBridge;
import net.minecraft.core.Direction;
import org.joml.Matrix3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** ModelPart's optimized cuboids bypass the vanilla Cube.compile normalization. */
@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.immediate.model.EntityRenderer", remap = false)
public abstract class SodiumEntityNormalsMixin {
    @WrapOperation(method = "prepareNormals(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;)V",
            at = @At(value = "INVOKE", target = "Lnet/caffeinemc/mods/sodium/api/math/MatrixHelper;transformNormal(Lorg/joml/Matrix3f;Lnet/minecraft/core/Direction;)I"),
            require = 6, expect = 6, allow = 6)
    private static int stardewcraft$trustedNormal(Matrix3f matrix, Direction direction,
            Operation<Integer> original, PoseStack.Pose pose) {
        return EmbeddiumNormalBridge.transformDirection(pose, direction);
    }
}
