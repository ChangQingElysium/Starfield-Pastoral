package com.stardew.craft.port;

import com.stardew.craft.port.net.neoforged.neoforge.client.extensions.IBlockEntityRendererExtension;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

/** NeoForge renderer-owned bounds for mod renderers; unrelated renderers retain Forge bounds. */
public final class PortBlockEntityRenderBounds {
    private PortBlockEntityRenderBounds() {}

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static AABB bounds(BlockEntityRenderDispatcher dispatcher, BlockEntity blockEntity) {
        BlockEntityRenderer<BlockEntity> renderer = dispatcher.getRenderer(blockEntity);
        if (renderer instanceof IBlockEntityRendererExtension extension) return extension.getRenderBoundingBox(blockEntity);
        if (renderer != null && renderer.getClass().getName().startsWith("com.stardew.craft.")) {
            return new AABB(blockEntity.getBlockPos());
        }
        return blockEntity.getRenderBoundingBox();
    }
}
