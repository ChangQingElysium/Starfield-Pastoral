package com.stardew.craft.block.utility;

import com.stardew.craft.blockentity.OfficeStoolBlockEntity;
import com.stardew.craft.entity.seat.SofaSeatEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

@SuppressWarnings("null")
public class OfficeChair2Block extends MapUtilityStaticBlock implements EntityBlock {
    public static final IntegerProperty COLOR = IntegerProperty.create("color", 0, WoodenChestColorPalette.size() - 1);
    private static final double SEAT_Y_OFFSET = 9.0D / 16.0D;

    public OfficeChair2Block(Properties properties, String modelId) {
        super(properties, modelId);
        registerDefaultState(defaultBlockState()
            .setValue(PART, Part.MAIN)
            .setValue(FACING, Direction.NORTH)
            .setValue(COLOR, WoodenChestColorPalette.defaultColorIndex()));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(COLOR);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        if (state.getValue(PART) != Part.MAIN) {
            return null;
        }
        return new OfficeStoolBlockEntity(pos, state);
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        BlockPos mainPos = pos;
        if (state.getValue(PART) == Part.EXTENSION) {
            BlockPos resolvedMain = findMainPos(level, pos, state);
            if (resolvedMain == null) {
                return InteractionResult.PASS;
            }
            BlockState resolvedState = level.getBlockState(resolvedMain);
            if (!(resolvedState.getBlock() instanceof OfficeChair2Block)) {
                return InteractionResult.PASS;
            }
            mainPos = resolvedMain;
        }

        SofaSeatEntity seat = SofaSeatEntity.getOrCreate((net.minecraft.server.level.ServerLevel) level, mainPos, SEAT_Y_OFFSET);
        if (seat == null) {
            return InteractionResult.PASS;
        }

        if (seat.isVehicle()) {
            return InteractionResult.CONSUME;
        }

        if (!player.startRiding(seat, false)) {
            return InteractionResult.PASS;
        }

        Vec3 seatPos = seat.position();
        player.teleportTo(seatPos.x, seatPos.y, seatPos.z);
        return InteractionResult.CONSUME;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide && state.getValue(PART) == Part.MAIN) {
            SofaSeatEntity.removeForPos((net.minecraft.server.level.ServerLevel) level, pos);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
