package com.stardew.craft.tree;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;

/** Match local canopy exposure in render snapshots, including mixed leaf species. */
public final class ForestCanopySnow {
    private ForestCanopySnow() {}

    public static boolean exposed(BlockGetter level, BlockPos pos) {
        var above = level.getBlockState(pos.above());
        return !above.is(BlockTags.LEAVES) && !above.canOcclude() && above.getFluidState().isEmpty();
    }
}
