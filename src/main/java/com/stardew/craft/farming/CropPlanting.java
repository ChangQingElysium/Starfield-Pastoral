package com.stardew.craft.farming;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/** Server placement gate shared by seeds, including random seed selections. */
public final class CropPlanting {
    private CropPlanting() {}

    public static boolean place(Level level, BlockPos root, BlockState crop) {
        boolean tall = crop.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF);
        if (!level.getBlockState(root).isAir() || !crop.canSurvive(level, root)
                || tall && !level.getBlockState(root.above()).isAir()) return false;
        if (!level.setBlock(root, crop, Block.UPDATE_ALL)) return false;
        // MinecraftForge delays onPlace during ItemStack.useOn. Capture both halves in
        // that same placement transaction instead of relying on the later callback.
        if (tall && level.getBlockState(root.above()).isAir()) {
            level.setBlock(root.above(), crop.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF,
                    DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
        }
        BlockState placed = level.getBlockState(root);
        BlockState upper = level.getBlockState(root.above());
        if (placed.is(crop.getBlock()) && placed.canSurvive(level, root)
                && (!tall || upper.is(crop.getBlock())
                    && upper.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER)) return true;
        // A protected/missing upper cell must not leave an invalid lower crop behind.
        if (placed.is(crop.getBlock())) level.setBlock(root, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        return false;
    }
}
