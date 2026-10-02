package com.stardew.craft.port.net.neoforged.neoforge.client.extensions;

import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

/**
 * NeoForge 21.1 {@code IBlockEntityRendererExtension} (inherited by every 1.21 {@link BlockEntityRenderer}).
 * Forge 1.20.1 culls block entities with {@code IForgeBlockEntity#getRenderBoundingBox()} on the block entity
 * instead; {@code PortLevelRendererBlockEntityBoundsMixin} makes {@code LevelRenderer} cull with this method for
 * every renderer that implements it, and with the NeoForge default (the unit cube at the block position) for all
 * other StardewCraft renderers, exactly like NeoForge {@code ClientHooks#isBlockEntityRendererVisible}.
 */
public interface IBlockEntityRendererExtension<T extends BlockEntity> {
    default AABB getRenderBoundingBox(T blockEntity) {
        return new AABB(blockEntity.getBlockPos());
    }
}
