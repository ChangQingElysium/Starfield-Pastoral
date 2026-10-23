package com.stardew.craft.platform.mixin.client.model.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import com.stardew.craft.platform.fabric.client.model.OrdinaryModelTransforms.RootTransformState;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Neo's root transforms can rotate arbitrary angles; axis-aligned winding would deform them. */
@Mixin(FaceBakery.class)
abstract class RootTransformWindingMixin {
    @WrapWithCondition(method = "bakeQuad", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/block/model/FaceBakery;recalculateWinding([ILnet/minecraft/core/Direction;)V"), require = 1)
    private boolean stardewcraft$preserveRootTransformedVertices(FaceBakery bakery, int[] vertices,
            Direction direction, @Local(argsOnly = true) ModelState state) {
        return !(state instanceof RootTransformState);
    }
}
