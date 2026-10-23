package com.stardew.craft.platform.fabric.client.blockentity;

import java.util.Objects;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

public final class BlockEntityVisibility {
    private BlockEntityVisibility() {}

    public static <T extends BlockEntity> boolean isVisible(BlockEntityRenderDispatcher dispatcher, T blockEntity, Frustum frustum) {
        return isVisible(dispatcher.getRenderer(blockEntity), blockEntity, frustum);
    }

    public static <T extends BlockEntity> boolean isVisible(net.minecraft.client.renderer.blockentity.BlockEntityRenderer<T> renderer,
            T blockEntity, Frustum frustum) {
        if (renderer == null) return false;
        // Vanilla renderers retain their native special visibility rules and do not implement this extension.
        if (!(renderer instanceof BlockEntityRenderer<?>)) return true;
        @SuppressWarnings("unchecked") var contextual = (BlockEntityRenderer<T>) renderer;
        AABB bounds = Objects.requireNonNull(contextual.getRenderBoundingBox(blockEntity), "block entity render bounds");
        // Native frustum arithmetic cannot reliably intersect an all-infinite box at every camera angle.
        return isInfinite(bounds) || frustum.isVisible(bounds);
    }

    /** Classifies owned renderers once, preventing a global renderer from also being drawn in its section. */
    public static <T extends BlockEntity> boolean collect(SectionCompiler.Results results, T blockEntity,
            net.minecraft.client.renderer.blockentity.BlockEntityRenderer<T> renderer) {
        if (!(renderer instanceof BlockEntityRenderer<?>)) return false;
        if (renderer.shouldRenderOffScreen(blockEntity)) results.globalBlockEntities.add(blockEntity);
        else results.blockEntities.add(blockEntity);
        return true;
    }

    private static boolean isInfinite(AABB bounds) {
        return Double.isInfinite(bounds.minX) && Double.isInfinite(bounds.minY) && Double.isInfinite(bounds.minZ)
                && Double.isInfinite(bounds.maxX) && Double.isInfinite(bounds.maxY) && Double.isInfinite(bounds.maxZ);
    }
}
