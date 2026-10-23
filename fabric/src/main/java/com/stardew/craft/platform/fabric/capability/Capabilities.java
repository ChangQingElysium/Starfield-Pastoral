package com.stardew.craft.platform.fabric.capability;

import com.stardew.craft.platform.fabric.fluid.IFluidHandler;
import com.stardew.craft.platform.fabric.item.IItemHandler;
import net.minecraft.core.Direction;

public final class Capabilities {
    private Capabilities() {}

    public static final class ItemHandler {
        public static final BlockCapability<IItemHandler, Direction> BLOCK = new BlockCapability<>(BlockCapability.Kind.ITEM);
        private ItemHandler() {}
    }

    public static final class FluidHandler {
        public static final BlockCapability<IFluidHandler, Direction> BLOCK = new BlockCapability<>(BlockCapability.Kind.FLUID);
        private FluidHandler() {}
    }
}
