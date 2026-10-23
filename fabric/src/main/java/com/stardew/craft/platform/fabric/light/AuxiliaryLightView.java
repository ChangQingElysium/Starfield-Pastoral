package com.stardew.craft.platform.fabric.light;

import net.minecraft.core.BlockPos;

/** A real chunk owner or an immutable render-region snapshot; never a block-entity lookup. */
public interface AuxiliaryLightView {
    AuxiliaryLightManager stardewcraft$auxiliaryLights(BlockPos pos);
}
