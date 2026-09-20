package com.stardew.craft.block.tree;

import com.stardew.craft.blockentity.NewTreePartBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Forge 1.20.1 port of the generated tree root/branch block.
 *
 * <p>The implementation intentionally keeps the source placement, shape and
 * marker-removal behavior unchanged.  Only the nested properties type and the
 * visibility of vanilla 1.20 overrides differ from the NeoForge source.</p>
 */
public class NewTreePartBlock extends Block implements EntityBlock {
	private final boolean requiresHorizontalClearance;

	@SuppressWarnings("null")
	public NewTreePartBlock(BlockBehaviour.Properties properties, boolean requiresHorizontalClearance) {
		super(properties);
		this.requiresHorizontalClearance = requiresHorizontalClearance;
	}

	public boolean requiresHorizontalClearance() {
		return requiresHorizontalClearance;
	}

	@SuppressWarnings("null")
	@Nullable
	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		if (requiresHorizontalClearance && !hasHorizontalClearance(context.getLevel(), context.getClickedPos())) {
			return null;
		}
		return defaultBlockState();
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

	@SuppressWarnings("null")
	@Override
	public RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@SuppressWarnings("null")
	@Override
	public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
			CollisionContext context) {
		return Shapes.block();
	}

	@SuppressWarnings("null")
	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return Shapes.block();
	}

	public static boolean hasHorizontalClearance(LevelReader level, BlockPos center) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (dx == 0 && dz == 0) {
					continue;
				}
				BlockPos pos = center.offset(dx, 0, dz);
				BlockState state = level.getBlockState(pos);
				if (!state.canBeReplaced()) {
					return false;
				}
			}
		}
		return true;
	}
}
