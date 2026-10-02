package com.stardew.craft.port.net.neoforged.neoforge.capabilities;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

/** PORT(1.20.1): the NeoForge capabilities the mod uses, bound to their Forge counterparts. */
public final class Capabilities {
    private Capabilities() {}

    public static final class ItemHandler {
        private ItemHandler() {}

        public static final BlockCapability<IItemHandler, Direction> BLOCK = new BlockCapability<>(
                new ResourceLocation("neoforge", "item_handler"), IItemHandler.class, Direction.class, ForgeCapabilities.ITEM_HANDLER);
    }

    public static final class FluidHandler {
        private FluidHandler() {}

        public static final BlockCapability<IFluidHandler, Direction> BLOCK = new BlockCapability<>(
                new ResourceLocation("neoforge", "fluid_handler"), IFluidHandler.class, Direction.class, ForgeCapabilities.FLUID_HANDLER);
    }
}
