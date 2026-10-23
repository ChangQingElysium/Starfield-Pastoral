package com.stardew.craft.platform.mixin.client.model.mixin;


import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.platform.fabric.client.model.ExtendedBakedModel;
import java.util.Objects;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Replaces only the native transform application, inside its pushed pose and before all rendering branches. */
@Mixin(ItemRenderer.class)
public abstract class ItemModelTransformMixin {
    @WrapOperation(method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/model/ItemTransform;apply(ZLcom/mojang/blaze3d/vertex/PoseStack;)V"), require = 1)
    private void stardewcraft$transform(ItemTransform transform, boolean leftHand, PoseStack pose, Operation<Void> original,
            @Local(argsOnly = true) LocalRef<BakedModel> model, @Local(argsOnly = true) ItemDisplayContext context) {
        if (model.get() instanceof ExtendedBakedModel extended) {
            model.set(Objects.requireNonNull(extended.applyTransform(context, pose, leftHand), "transformed model"));
        } else original.call(transform, leftHand, pose);
    }
}
