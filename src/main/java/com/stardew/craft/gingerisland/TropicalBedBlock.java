package com.stardew.craft.gingerisland;

import com.stardew.craft.block.decor.BedDecorBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/** The approved bed is two lanes by three cells, with its head at local Z=2. */
public final class TropicalBedBlock extends BedDecorBlock {
    public TropicalBedBlock(Properties properties, String model) {
        super(properties, model, true);
    }

    @Override protected boolean usesAuthoredShape() { return true; }
    @Override public double sleepYOffset() { return 15.0 / 16.0; }

    @Override
    public BlockPos sleepAnchor(BlockGetter level, BlockPos pos, BlockState state) {
        BlockPos main = resolveMainPos(level, pos, state);
        Direction facing = state.getValue(FACING);
        CellOffset clicked = new CellOffset(pos.getX() - main.getX(), 0, pos.getZ() - main.getZ()).unrotateY(facing);
        CellOffset head = new CellOffset(clicked.dx() > 0 ? 1 : 0, 0, 2).rotateY(facing);
        BlockPos target = main.offset(head.dx(), head.dy(), head.dz());
        return level.getBlockState(target).is(this) ? target : main;
    }
}
