// Upstream-SHA256: c4093934e86ebf5762b0b64130ee834e76d1aa47326b7353e9252d9f650175b7
package com.stardew.craft.fluid;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.platform.fabric.fluid.FishPondWaterFluid;
import com.stardew.craft.platform.fabric.fluid.PondWaterTint;
import com.stardew.craft.platform.fabric.registry.DeferredHolder;
import com.stardew.craft.platform.fabric.registry.DeferredRegister;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;

/** Native fluid registration; this platform has no synthetic FluidType registry. */
public final class ModFluids {
    public static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(BuiltInRegistries.FLUID, StardewCraft.MODID);

    public static final DeferredHolder<Fluid, FlowingFluid> FISH_POND_WATER =
            FLUIDS.register("fish_pond_water", ModFluids::createSource);
    public static final DeferredHolder<Fluid, FlowingFluid> FLOWING_FISH_POND_WATER =
            FLUIDS.register("flowing_fish_pond_water", ModFluids::createFlowing);

    public static final int DEFAULT_POND_WATER_COLOR = PondWaterTint.DEFAULT_COLOR;

    private ModFluids() {}

    private static FlowingFluid createSource() {
        return new FishPondWaterFluid.Source(FISH_POND_WATER, FLOWING_FISH_POND_WATER,
                () -> (LiquidBlock) ModBlocks.FISH_POND_WATER.get());
    }

    private static FlowingFluid createFlowing() {
        return new FishPondWaterFluid.Flowing(FISH_POND_WATER, FLOWING_FISH_POND_WATER,
                () -> (LiquidBlock) ModBlocks.FISH_POND_WATER.get());
    }
}
