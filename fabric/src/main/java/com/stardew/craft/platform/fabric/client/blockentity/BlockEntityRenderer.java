package com.stardew.craft.platform.fabric.client.blockentity;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

/** Native renderer with the contextual visible bounds used by StardewCraft's renderers. */
public interface BlockEntityRenderer<T extends BlockEntity> extends net.minecraft.client.renderer.blockentity.BlockEntityRenderer<T> {
    /** Renderer overrides are authoritative; the default is the anchor's unit cube. */
    default AABB getRenderBoundingBox(T blockEntity) {
        return new AABB(blockEntity.getBlockPos());
    }
}
