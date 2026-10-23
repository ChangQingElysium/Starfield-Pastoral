package com.stardew.craft.platform.fabric.light;

import java.util.Map;
import net.minecraft.core.BlockPos;

/** Immutable light data captured for a render region before its worker starts. */
public final class AuxiliaryLightSnapshot implements AuxiliaryLightManager {
    private final Map<Long, Byte> lights;
    public AuxiliaryLightSnapshot(Map<Long, Byte> lights) { this.lights = Map.copyOf(lights); }
    @Override public int getLightAt(BlockPos pos) { return lights.getOrDefault(pos.asLong(), (byte) 0); }
    @Override public void setLightAt(BlockPos pos, int value) { throw new UnsupportedOperationException("Render light snapshot is read-only"); }
}
