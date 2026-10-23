package com.stardew.craft.platform.fabric.capability;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public final class BlockCapabilities {
    private BlockCapabilities() {}

    public static <T, C> T get(BlockCapability<T, C> capability, Level level, BlockPos position, C context) {
        return capability.getCapability(level, position, context);
    }
}
