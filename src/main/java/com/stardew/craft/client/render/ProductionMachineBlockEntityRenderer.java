package com.stardew.craft.client.render;

import com.stardew.craft.block.utility.UtilityMachineRenderState;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Skip empty static machines before the dispatcher samples light or calls their renderer. */
public interface ProductionMachineBlockEntityRenderer<T extends BlockEntity> extends BlockEntityRenderer<T> {
    boolean isReadyForBubble(T blockEntity);

    @Override
    default boolean shouldRender(T blockEntity, Vec3 cameraPos) {
        return (UtilityMachineRenderState.rendersDynamicBody(blockEntity.getBlockState())
                || isReadyForBubble(blockEntity)) && BlockEntityRenderer.super.shouldRender(blockEntity, cameraPos);
    }

    @Override
    default AABB getRenderBoundingBox(T blockEntity) {
        // Cover the two-cell machine body, working scale and billboard corners.
        // Existing renderer overrides remain authoritative (including potted crops).
        AABB bounds = BlockEntityRenderer.super.getRenderBoundingBox(blockEntity)
                .inflate(1.25, .5, 1.25).expandTowards(0, 2, 0);
        if (blockEntity.getLevel() != null && isReadyForBubble(blockEntity)) {
            double top = blockEntity.getBlockPos().getY() + BubbleYHelper.get(blockEntity.getBlockState(),
                    blockEntity.getLevel(), blockEntity.getBlockPos()) + .75;
            if (top > bounds.maxY) bounds = new AABB(bounds.minX, bounds.minY, bounds.minZ,
                    bounds.maxX, top, bounds.maxZ);
        }
        return bounds;
    }
}
