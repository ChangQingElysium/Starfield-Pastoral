package com.stardew.craft.gingerisland;

import com.stardew.craft.block.decor.MapDecorStaticBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;

/** Each reserved boat cell retains the water that it actually replaces. */
final class GingerIslandBoatWater {
    private GingerIslandBoatWater() {}

    static BlockState extension(Level level, BlockPos target, BlockState main) {
        return main.setValue(MapDecorStaticBlock.PART, MapDecorStaticBlock.Part.EXTENSION)
                .setValue(BlockStateProperties.WATERLOGGED,
                        level.getFluidState(target).is(Fluids.WATER));
    }
}
