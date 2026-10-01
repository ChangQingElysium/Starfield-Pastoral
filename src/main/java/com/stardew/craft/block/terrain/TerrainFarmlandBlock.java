package com.stardew.craft.block.terrain;

import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Native farmland shape, moisture, crop and hydration hooks, with authored seasonal surfaces. */
public class TerrainFarmlandBlock extends FarmBlock {

    public TerrainFarmlandBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().canSurvive(context.getLevel(), context.getClickedPos())
                ? defaultBlockState() : TerrainSoils.substrate(defaultBlockState()).defaultBlockState();
    }

    public static final class Infertile extends TerrainFarmlandBlock {
        public Infertile(Properties properties) { super(properties); }
    }

    public static final class Sandy extends TerrainFarmlandBlock {
        public Sandy(Properties properties) { super(properties); }
    }
}
