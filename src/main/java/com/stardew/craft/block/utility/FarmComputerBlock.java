package com.stardew.craft.block.utility;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import com.stardew.craft.port.net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;

public class FarmComputerBlock extends MapUtilityStaticBlock {

    public FarmComputerBlock(Properties properties) {
        super(properties, "stardewcraft:block/utility/farm_computer");
        // Missing facing in old saves resolves to the former fixed model orientation.
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.WEST));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        if (state.getValue(PART) == Part.EXTENSION) return List.of();
        return List.of(new ItemStack(ModBlocks.FARM_COMPUTER.get()));
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return ItemInteractionResult.sidedSuccess(true);
        }
        if (player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel) {
            BlockPos mainPos = findMainPos(level, pos, state);
            if (mainPos != null) {
                MachineModelFootprint.repair(level, mainPos, level.getBlockState(mainPos));
                showReport(serverLevel, mainPos, serverPlayer);
            }
            return ItemInteractionResult.sidedSuccess(false);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel) {
            BlockPos mainPos = findMainPos(level, pos, state);
            if (mainPos != null) {
                MachineModelFootprint.repair(level, mainPos, level.getBlockState(mainPos));
                showReport(serverLevel, mainPos, serverPlayer);
            }
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    private static void showReport(ServerLevel level, BlockPos pos, ServerPlayer player) {
        level.playSound(null, pos, ModSounds.DWARVISH_SENTRY.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        com.stardew.craft.time.StardewSimulationTaskScheduler.schedule(level, 10, () -> {
            if (player.isRemoved() || player.connection == null) {
                return;
            }
            FarmComputerReport.create(level, pos, player).sendTo(player);
        });
    }
}
