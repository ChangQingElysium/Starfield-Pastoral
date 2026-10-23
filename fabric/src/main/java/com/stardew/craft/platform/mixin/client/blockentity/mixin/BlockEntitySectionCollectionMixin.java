package com.stardew.craft.platform.mixin.client.blockentity.mixin;


import com.stardew.craft.platform.fabric.client.blockentity.BlockEntityVisibility;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SectionCompiler.class)
abstract class BlockEntitySectionCollectionMixin {
    @Shadow @Final private BlockEntityRenderDispatcher blockEntityRenderer;

    @Inject(method = "handleBlockEntity", at = @At("HEAD"), cancellable = true, require = 1)
    private <T extends BlockEntity> void stardewcraft$exclusiveGlobalCollection(SectionCompiler.Results results, T entity, CallbackInfo result) {
        if (BlockEntityVisibility.collect(results, entity, blockEntityRenderer.getRenderer(entity))) result.cancel();
    }
}
