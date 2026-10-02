package com.stardew.craft.mixin;

import com.stardew.craft.port.PortBlockEntityRenderBounds;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * PORT(1.20.1): NeoForge 21.1 frustum-culls block entities with the renderer's
 * {@code BlockEntityRenderer#getRenderBoundingBox(T)} (default: unit cube at the block position); Forge 1.20.1 uses
 * {@code BlockEntity#getRenderBoundingBox()} (default: collision-shape bounds, infinite when there is none). For
 * StardewCraft renderers the 1.21 renderer bounds are used; vanilla and other mods keep Forge's behaviour.
 * {@code require = 0}: Embeddium replaces this loop and uses SodiumBlockEntityBoundsMixin instead.
 */
@Mixin(LevelRenderer.class)
public abstract class PortLevelRendererBlockEntityBoundsMixin {
    @Shadow
    @Final
    private BlockEntityRenderDispatcher blockEntityRenderDispatcher;

    @Redirect(method = "renderLevel", require = 0, expect = 2, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/entity/BlockEntity;getRenderBoundingBox()Lnet/minecraft/world/phys/AABB;",
            remap = false)) // Forge IForgeBlockEntity method (not obfuscated)
    private AABB stardewcraft$rendererBounds(BlockEntity blockEntity) {
        return PortBlockEntityRenderBounds.bounds(blockEntityRenderDispatcher, blockEntity);
    }
}
