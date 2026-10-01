package com.stardew.craft.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/** Large scenery follows the loaded view distance instead of the default four-chunk cutoff. */
public interface LargeDecorBlockEntityRenderer<T extends BlockEntity> extends BlockEntityRenderer<T>,
        com.stardew.craft.port.net.neoforged.neoforge.client.extensions.IBlockEntityRendererExtension<T> {
    @Override
    default int getViewDistance() {
        return Minecraft.getInstance().options.getEffectiveRenderDistance() * 16;
    }

    @Override
    default boolean shouldRender(T blockEntity, Vec3 cameraPos) {
        double distance = getViewDistance();
        // An object's near edge can be visible while its anchor is already beyond the cutoff.
        // Keep the normal frustum/section checks and each renderer's full model bounds.
        return getRenderBoundingBox(blockEntity).distanceToSqr(cameraPos) < distance * distance;
    }
}
