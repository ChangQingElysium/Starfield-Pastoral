package com.stardew.craft.platform.fabric.light;

import net.minecraft.core.BlockPos;

/** A chunk's auxiliary block light, safe to read from a lighting worker. */
public interface AuxiliaryLightManager {
    int getLightAt(BlockPos pos);
    void setLightAt(BlockPos pos, int value);
    default void removeLightAt(BlockPos pos) { setLightAt(pos, 0); }
}
