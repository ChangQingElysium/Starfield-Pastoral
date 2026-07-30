package com.stardew.craft.block.utility;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.blockentity.InsertResult;
import com.stardew.craft.blockentity.ModBlockEntities;
import com.stardew.craft.blockentity.MissingItemRequirement;
import com.stardew.craft.blockentity.RecyclingMachineBlockEntity;
import com.stardew.craft.blockentity.UtilityDropHelper;
import com.stardew.craft.network.MissingItemHudMessagePacket;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.util.List;

public class RecyclingMachineBlock extends MapUtilityStaticBlock implements EntityBlock {
	public static final BooleanProperty WORKING = BooleanProperty.create("working");

	@SuppressWarnings("null")
	public RecyclingMachineBlock(Properties properties) {
		super(properties, "stardewcraft:block/utility/recycling_machine");
		registerDefaultState(defaultBlockState().setValue(WORKING, false));
	}

	@SuppressWarnings("null")
	@Override
	protected void createBlockStateDefinition(@SuppressWarnings("null") StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(WORKING);
	}

	@Override
	public RenderShape getRenderShape(@SuppressWarnings("null") BlockState state) {
		return RenderShape.ENTITYBLOCK_ANIMATED;
	}

	@SuppressWarnings("null")
	@Override
	protected List<ItemStack> getDrops(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") LootParams.Builder params) {
		if (state.getValue(PART) == Part.EXTENSION) {
			return List.of();
		}
		return List.of(new ItemStack(ModBlocks.RECYCLING_MACHINE.get()));
	}

	@SuppressWarnings("null")
	@Override
	@Nullable
	public BlockEntity newBlockEntity(@SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") BlockState state) {
		if (state.getValue(PART) == Part.EXTENSION) {
			return null;
		}
		return new RecyclingMachineBlockEntity(pos, state);
	}

	@SuppressWarnings("null")
	@Override
	@Nullable
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(@SuppressWarnings("null") Level level, @SuppressWarnings("null") BlockState state, @SuppressWarnings("null") BlockEntityType<T> type) {
		if (state.getValue(PART) == Part.EXTENSION) {
			return null;
		}
		if (type != ModBlockEntities.RECYCLING_MACHINE.get()) {
			return null;
		}
		if (level.isClientSide) {
			return (lvl, pos, st, be) -> RecyclingMachineBlockEntity.clientTick(lvl, pos, st, (RecyclingMachineBlockEntity) be);
		}
		return (lvl, pos, st, be) -> RecyclingMachineBlockEntity.serverTick(lvl, pos, st, (RecyclingMachineBlockEntity) be);
	}

	@SuppressWarnings("null")
	@Override
	protected ItemInteractionResult useItemOn(@SuppressWarnings("null") ItemStack stack, @SuppressWarnings("null") BlockState state, @SuppressWarnings("null") Level level, @SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") Player player, @SuppressWarnings("null") InteractionHand hand, @SuppressWarnings("null") BlockHitResult hit) {
		if (state.getValue(PART) == Part.EXTENSION) {
			BlockPos mainPos = findMainPos(level, pos, state);
			if (mainPos == null) {
				return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
			}
			return useItemOn(stack, level.getBlockState(mainPos), level, mainPos, player, hand, hit);
		}

		if (level.isClientSide) {
			return ItemInteractionResult.sidedSuccess(true);
		}

		BlockEntity be = level.getBlockEntity(pos);
		if (!(be instanceof RecyclingMachineBlockEntity machine)) {
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		}

		if (UtilityDropHelper.tryHarvest(level, pos, player, machine::isReady, machine::harvestOne,
				UtilityDropHelper.LOW_MACHINE_VANILLA_XP)) {
			return ItemInteractionResult.sidedSuccess(false);
		}

		if (!stack.isEmpty()) {
			InsertResult result = machine.tryInsertWithResult(stack, player);
			if (result.inserted()) {
				if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
					com.stardew.craft.player.PlayerDataManager.getPlayerData(serverPlayer)
						.incrementStat("piecesOfTrashRecycled", 1);
				}
				if (state.hasProperty(WORKING) && !state.getValue(WORKING)) {
					level.setBlock(pos, state.setValue(WORKING, true), 3);
				}
				level.playSound(null, pos, ModSounds.TRASHCAN.get(), SoundSource.BLOCKS, 0.9f, 1.0f);
				return ItemInteractionResult.sidedSuccess(false);
			}
			MissingItemRequirement missing = result.missingRequirement();
			if (missing != null) {
				MissingItemHudMessagePacket.sendTo(player, missing.item(), missing.requiredCount());
				return ItemInteractionResult.sidedSuccess(false);
			}
		}

		return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
	}

	@SuppressWarnings("null")
	@Override
	protected InteractionResult useWithoutItem(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") Level level, @SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") Player player, @SuppressWarnings("null") BlockHitResult hit) {
		if (state.getValue(PART) == Part.EXTENSION) {
			BlockPos mainPos = findMainPos(level, pos, state);
			if (mainPos == null) {
				return InteractionResult.PASS;
			}
			return useWithoutItem(level.getBlockState(mainPos), level, mainPos, player, hit);
		}

		if (level.isClientSide) {
			return InteractionResult.SUCCESS;
		}

		BlockEntity be = level.getBlockEntity(pos);
		if (!(be instanceof RecyclingMachineBlockEntity machine)) {
			return InteractionResult.PASS;
		}

		return UtilityDropHelper.tryHarvest(level, pos, player, machine::isReady, machine::harvestOne,
				UtilityDropHelper.LOW_MACHINE_VANILLA_XP)
			? InteractionResult.CONSUME
			: InteractionResult.PASS;
	}

	@SuppressWarnings("null")
	@Override
	public void onRemove(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") Level level, @SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") BlockState newState, boolean isMoving) {
		if (!state.is(newState.getBlock()) && !isMoving && state.getValue(PART) == Part.MAIN) {
			UtilityDropHelper.dropAutomationContents(level, pos);
		}
		super.onRemove(state, level, pos, newState, isMoving);
	}

	@SuppressWarnings("null")
	public BlockPos getMainPos(BlockGetter level, BlockPos pos, BlockState state) {
		return findMainPos(level, pos, state);
	}
}
