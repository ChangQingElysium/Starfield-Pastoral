package com.stardew.craft.platform.fabric.fluid;

import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/** Only dripstone/cauldron interpretation changes; the world's fluid stays distinct. */
public final class PondWaterDrips {
    private PondWaterDrips() {}

    public static Fluid cauldronFluid(Fluid fluid) {
        return fluid instanceof FishPondWaterFluid ? Fluids.WATER : fluid;
    }

    public static boolean canFillCauldron(Fluid fluid) {
        return fluid instanceof FishPondWaterFluid;
    }
}
