package com.stardew.craft.platform.fabric.fluid.transfer;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;

public interface TransactionalFluidHandler {
    Storage<FluidVariant> stardewcraft$fluidStorage();
}
