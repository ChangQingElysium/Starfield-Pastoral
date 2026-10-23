package com.stardew.craft.platform.fabric.fluid;

/** Existing gameplay's millibucket contract; Fabric transfers require a transaction bridge. */
public interface IFluidHandler {
    int getTanks();
    FluidStack getFluidInTank(int tank);
    int getTankCapacity(int tank);
    boolean isFluidValid(int tank, FluidStack stack);
    int fill(FluidStack resource, FluidAction action);
    FluidStack drain(FluidStack resource, FluidAction action);
    FluidStack drain(int maxDrain, FluidAction action);

    enum FluidAction {
        EXECUTE, SIMULATE;
        public boolean execute() { return this == EXECUTE; }
        public boolean simulate() { return this == SIMULATE; }
    }
}
