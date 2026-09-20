package com.stardew.craft.block.tree;

import com.stardew.craft.blockentity.NewTreePartBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Forge 1.20.1 port of the generated tree log block. */
public class NewTreeLogBlock extends RotatedPillarBlock implements EntityBlock {
	public NewTreeLogBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	@Nullable
	public BlockEntity newBlockEntity(@SuppressWarnings("null") BlockPos pos,
			@SuppressWarnings("null") BlockState state) {
		return new NewTreePartBlockEntity(pos, state);
	}

	@SuppressWarnings("null")
	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
		if (!level.isClientSide && !isMoving && !state.equals(newState)
				&& level.getBlockEntity(pos) instanceof NewTreePartBlockEntity treePart) {
			treePart.clearGeneratedTreeMarker();
		}
		super.onRemove(state, level, pos, newState, isMoving);
	}
}
