package com.stardew.craft.block.decor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/** Two neighboring shop doors form a pair, even if the first leaf had the wrong hinge. */
public final class ShopGlassDoorBlock extends DoorBlock {
    public ShopGlassDoorBlock(Properties properties) {
        super(properties, BlockSetType.OAK); // PORT(1.20.1): 1.20.1 constructor takes Properties first
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide) return;

        Direction facing = state.getValue(FACING);
        Direction clockwise = facing.getClockWise();
        boolean nextClockwise = matchingDoor(level, pos.relative(clockwise), facing);
        boolean nextCounterclockwise = matchingDoor(level, pos.relative(clockwise.getOpposite()), facing);
        if (nextClockwise == nextCounterclockwise) return;

        Direction towardNeighbor = nextClockwise ? clockwise : clockwise.getOpposite();
        BlockPos neighbor = pos.relative(towardNeighbor);
        // A third leaf must not change an existing pair farther along the row.
        if (matchingDoor(level, neighbor.relative(towardNeighbor), facing)) return;

        DoorHingeSide hinge = nextClockwise ? DoorHingeSide.LEFT : DoorHingeSide.RIGHT;
        setHinge(level, pos, hinge);
        setHinge(level, neighbor, hinge == DoorHingeSide.LEFT ? DoorHingeSide.RIGHT : DoorHingeSide.LEFT);
    }

    private boolean matchingDoor(Level level, BlockPos pos, Direction facing) {
        BlockState lower = level.getBlockState(pos);
        BlockState upper = level.getBlockState(pos.above());
        return lower.is(this) && lower.getValue(HALF) == DoubleBlockHalf.LOWER
                && lower.getValue(FACING) == facing && upper.is(this)
                && upper.getValue(HALF) == DoubleBlockHalf.UPPER && upper.getValue(FACING) == facing;
    }

    private void setHinge(Level level, BlockPos pos, DoorHingeSide hinge) {
        for (int y = 0; y < 2; y++) {
            BlockPos part = pos.above(y);
            level.setBlock(part, level.getBlockState(part).setValue(HINGE, hinge), UPDATE_ALL);
        }
    }
}
