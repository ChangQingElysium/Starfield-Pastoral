package com.stardew.craft.block.utility;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.blockentity.UtilityDropHelper;
import com.stardew.craft.blockentity.ModBlockEntities;
import com.stardew.craft.blockentity.PreservesJarBlockEntity;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import com.stardew.craft.port.net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
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

/**
 * Preserves Jar - turns crops/roe into jelly, pickles, and aged roe.
 */
public class PreservesJarBlock extends MapUtilityStaticBlock implements EntityBlock {
	public static final BooleanProperty WORKING = BooleanProperty.create("working");

	@SuppressWarnings("null")
	public PreservesJarBlock(Properties properties) {
		super(properties, "stardewcraft:block/utility/preserves_jar");
		registerDefaultState(defaultBlockState()
			.setValue(FACING, Direction.NORTH)
			.setValue(WORKING, false));
	}

	@Override
	protected void createBlockStateDefinition(@SuppressWarnings("null") StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
        builder.add(WORKING);
	}

	@Override
	public RenderShape getRenderShape(@SuppressWarnings("null") BlockState state) {
		return UtilityMachineRenderState.forWorkingState(
			state.getValue(WORKING), state.getValue(PART) == Part.EXTENSION);
	}

	@SuppressWarnings("null")
	@Override
	protected List<ItemStack> getDrops(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") LootParams.Builder params) {
		if (state.getValue(PART) == Part.EXTENSION) return List.of();
        return List.of(new ItemStack(ModBlocks.PRESERVES_JAR.get()));
	}

	@Override
	@Nullable
	public BlockEntity newBlockEntity(@SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") BlockState state) {
		if (state.getValue(PART) == Part.EXTENSION) return null;
        return new PreservesJarBlockEntity(pos, state);
	}

	@Override
	@Nullable
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(@SuppressWarnings("null") Level level, @SuppressWarnings("null") BlockState state, @SuppressWarnings("null") BlockEntityType<T> type) {
        if (state.getValue(PART) == Part.EXTENSION) return null;
		if (type != ModBlockEntities.PRESERVES_JAR.get()) {
			return null;
		}
		if (level.isClientSide) {
			return (lvl, pos, st, be) -> PreservesJarBlockEntity.clientTick(lvl, pos, st, (PreservesJarBlockEntity) be);
		}
		return (lvl, pos, st, be) -> PreservesJarBlockEntity.serverTick(lvl, pos, st, (PreservesJarBlockEntity) be);
	}

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection();
        if (!canPlaceAtFacing(context.getLevel(), context.getClickedPos(), facing, context)) return null;
        return defaultBlockState().setValue(FACING, facing).setValue(PART, Part.MAIN);
    }

	@SuppressWarnings("null")
	@Override
	protected ItemInteractionResult useItemOn(@SuppressWarnings("null") ItemStack stack, @SuppressWarnings("null") BlockState state, @SuppressWarnings("null") Level level, @SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") Player player, @SuppressWarnings("null") InteractionHand hand, @SuppressWarnings("null") BlockHitResult hit) {
        if (state.getValue(PART) == Part.EXTENSION) return super.useItemOn(stack, state, level, pos, player, hand, hit);
		if (level.isClientSide) {
			return ItemInteractionResult.sidedSuccess(true);
		}

		BlockEntity be = level.getBlockEntity(pos);
		if (!(be instanceof PreservesJarBlockEntity jar)) {
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		}

		if (UtilityDropHelper.tryHarvest(level, pos, player, jar::isReady, jar::harvestOne,
				UtilityDropHelper.STANDARD_MACHINE_VANILLA_XP)) {
			return ItemInteractionResult.sidedSuccess(false);
		}

		if (!stack.isEmpty() && jar.tryInsert(stack, player)) {
			if (state.hasProperty(WORKING) && !state.getValue(WORKING)) {
				level.setBlock(pos, state.setValue(WORKING, true), 3);
			}
			level.playSound(null, pos, ModSounds.SHIP.get(), SoundSource.BLOCKS, 0.9f, 1.0f);
			return ItemInteractionResult.sidedSuccess(false);
		}

		return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
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
	@Override
	protected InteractionResult useWithoutItem(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") Level level, @SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") Player player, @SuppressWarnings("null") BlockHitResult hit) {
        if (state.getValue(PART) == Part.EXTENSION) return super.useWithoutItem(state, level, pos, player, hit);
		if (level.isClientSide) {
			return InteractionResult.SUCCESS;
		}

		BlockEntity be = level.getBlockEntity(pos);
		if (!(be instanceof PreservesJarBlockEntity jar)) {
			return InteractionResult.PASS;
		}

		return UtilityDropHelper.tryHarvest(level, pos, player, jar::isReady, jar::harvestOne,
				UtilityDropHelper.STANDARD_MACHINE_VANILLA_XP)
			? InteractionResult.CONSUME
			: InteractionResult.PASS;
	}
}
