package com.stardew.craft.block.utility;

import com.stardew.craft.entity.seat.SofaSeatEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

@SuppressWarnings("null")
public class DyeableChairBlock extends MapUtilityStaticBlock {
    public static final IntegerProperty COLOR = IntegerProperty.create("color", 0, WoodenChestColorPalette.size() - 1);
    private final double seatYOffset;

    public DyeableChairBlock(Properties properties, String modelId, double seatYOffset) {
        super(properties, modelId);
        this.seatYOffset = seatYOffset;
        registerDefaultState(defaultBlockState()
            .setValue(PART, Part.MAIN)
            .setValue(FACING, net.minecraft.core.Direction.NORTH)
            .setValue(COLOR, WoodenChestColorPalette.defaultColorIndex()));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(COLOR);
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        BlockPos mainPos = resolveMainPos(level, pos, state);
        BlockState mainState = level.getBlockState(mainPos);
        if (!(mainState.getBlock() instanceof DyeableChairBlock)) {
            return InteractionResult.PASS;
        }

        if (com.stardew.craft.npc.runtime.SamSupportTarget.occupied((net.minecraft.server.level.ServerLevel)level,mainPos))
            return InteractionResult.CONSUME;

        SofaSeatEntity seat = SofaSeatEntity.getOrCreate((net.minecraft.server.level.ServerLevel) level, mainPos, seatYOffset);
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
        player.setYBodyRot(mainState.getValue(FACING).toYRot() + 180.0F);
        player.setYRot(mainState.getValue(FACING).toYRot() + 180.0F);
        return InteractionResult.CONSUME;
    }

    public BlockPos resolveMainPos(Level level, BlockPos pos, BlockState state) {
        if (state.getValue(PART) == Part.MAIN) {
            return pos;
        }
        BlockPos resolved = findMainPos(level, pos, state);
        if (resolved != null) {
            return resolved;
        }

        // Fallback for mismatched extension offsets: find nearest MAIN of same block.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos candidatePos = pos.offset(dx, dy, dz);
                    BlockState candidateState = level.getBlockState(candidatePos);
                    if (candidateState.getBlock() == this
                        && candidateState.hasProperty(PART)
                        && candidateState.getValue(PART) == Part.MAIN) {
                        return candidatePos;
                    }
                }
            }
        }
        return pos;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) {
            BlockPos seatPos = resolveMainPos(level, pos, state);
            SofaSeatEntity.removeForPos((net.minecraft.server.level.ServerLevel) level, seatPos);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
