package com.stardew.craft.block.casino;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * The authored club computer is 39 model pixels tall. Minecraft block models
 * only allow element coordinates through Y=32, so the visible upper assembly
 * is rendered by one dedicated extension cell instead of sinking the whole
 * machine seven pixels into the floor.
 */
public final class ClubComputerBlock extends CasinoInteractiveBlock {
    public static final IntegerProperty MODEL_SLICE = IntegerProperty.create("model_slice", 0, 2);
    public static final int MAIN_SLICE = 0;
    public static final int UPPER_SLICE = 1;
    public static final int EMPTY_SLICE = 2;

    public ClubComputerBlock(Properties properties) {
        super(properties,
                "stardewcraft:casino/club_computer",
                -15.0D, 0.0D, 0.0D, 15.0D, 39.0D, 16.0D,
                Kind.CLUB_COMPUTER);
        registerDefaultState(defaultBlockState().setValue(MODEL_SLICE, MAIN_SLICE));
    }

    @Override
    protected void createBlockStateDefinition(
            @Nonnull StateDefinition.Builder<Block, BlockState> builder
    ) {
        super.createBlockStateDefinition(builder);
        builder.add(MODEL_SLICE);
    }

    @Override
    public void setPlacedBy(
            @Nonnull Level level,
            @Nonnull BlockPos pos,
            @Nonnull BlockState state,
            @Nullable LivingEntity placer,
            @Nonnull ItemStack stack
    ) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide || state.getValue(PART) != Part.MAIN) {
            return;
        }
        Direction facing = state.getValue(FACING);
        for (CellOffset offset : occupiedOffsets(facing)) {
            if (offset.dx() == 0 && offset.dy() == 0 && offset.dz() == 0) {
                continue;
            }
            BlockPos extensionPos = pos.offset(offset.dx(), offset.dy(), offset.dz());
            BlockState extension = level.getBlockState(extensionPos);
            if (!extension.is(this) || extension.getValue(PART) != Part.EXTENSION) {
                continue;
            }
            int slice = offset.dx() == 0 && offset.dy() == 1 && offset.dz() == 0
                    ? UPPER_SLICE
                    : EMPTY_SLICE;
            level.setBlock(extensionPos, extension.setValue(MODEL_SLICE, slice), 3);
        }
    }
}
