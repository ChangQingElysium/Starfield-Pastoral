package com.stardew.craft.platform.fabric.item.transfer;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.BeetrootBlock;
import net.minecraft.world.level.block.CarrotBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.PotatoBlock;
import net.minecraft.world.level.block.TorchflowerCropBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Exact reachable vanilla CropBlock implementations; arbitrary Bonemealable side effects need their own planner. */
public final class NativeCropPlans {
    private NativeCropPlans() {}
    public static boolean supported(BlockState state) {
        Class<?> type = state.getBlock().getClass();
        return type == CropBlock.class || type == CarrotBlock.class || type == PotatoBlock.class
                || type == BeetrootBlock.class || type == TorchflowerCropBlock.class;
    }
    public static BlockState bonemeal(BlockState state, RandomSource random) {
        if (!supported(state)) throw new UnsupportedOperationException("No transactional bonemeal planner for " + state.getBlock().getClass().getName());
        CropBlock crop = (CropBlock) state.getBlock();
        if (crop.isMaxAge(state)) throw new IllegalArgumentException("A mature vanilla crop is not a valid bonemeal target");
        // CropBlock.growCrops/getBonemealAgeIncrease; the only two native overrides are below.
        int growth = state.getBlock() instanceof TorchflowerCropBlock ? 1 : random.nextInt(4) + 2;
        if (state.getBlock() instanceof BeetrootBlock) growth /= 3;
        return crop.getStateForAge(Math.min(crop.getMaxAge(), crop.getAge(state) + growth));
    }
}
