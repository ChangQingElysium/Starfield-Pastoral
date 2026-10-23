package com.stardew.craft.platform.fabric.fluid;

import java.util.Objects;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/** Millibucket value used by the existing garden-pot handler; not Transfer droplets. */
public final class FluidStack {
    public static final FluidStack EMPTY = new FluidStack(Fluids.EMPTY, 0);
    private final Fluid fluid;
    private final int amount;
    private final DataComponentPatch components;

    public FluidStack(Fluid fluid, int amount) {
        this(fluid, amount, DataComponentPatch.EMPTY);
    }

    public FluidStack(Fluid fluid, int amount, DataComponentPatch components) {
        this.fluid = Objects.requireNonNull(fluid, "fluid");
        this.amount = amount;
        this.components = Objects.requireNonNull(components, "components");
    }

    public boolean isEmpty() { return amount <= 0 || fluid == Fluids.EMPTY; }
    public Fluid getFluid() { return isEmpty() ? Fluids.EMPTY : fluid; }
    public int getAmount() { return isEmpty() ? 0 : amount; }
    public DataComponentPatch getComponentsPatch() { return components; }
    public boolean is(TagKey<Fluid> tag) { return !isEmpty() && fluid.is(tag); }
    public boolean is(Fluid other) { return getFluid() == other; }
    public FluidStack copyWithAmount(int amount) { return new FluidStack(fluid, amount, components); }
}
