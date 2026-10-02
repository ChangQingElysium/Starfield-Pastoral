package com.stardew.craft.port.net.neoforged.neoforge.common.world;

import net.minecraft.core.BlockPos;

/** PORT(1.20.1): NeoForge per-chunk store for block-entity-driven light levels (see {@code PortAuxLight}). */
public interface AuxiliaryLightManager {
    void setLightAt(BlockPos pos, int value);

    default void removeLightAt(BlockPos pos) {
        setLightAt(pos, 0);
    }

    int getLightAt(BlockPos pos);
}
