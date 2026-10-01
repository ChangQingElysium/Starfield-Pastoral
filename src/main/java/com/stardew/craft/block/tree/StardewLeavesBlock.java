package com.stardew.craft.block.tree;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class StardewLeavesBlock extends LeavesBlock {
	public static final BooleanProperty DORMANT = BooleanProperty.create("dormant");
	private static final int FAST_DECAY_DELAY = 3;
	private static volatile int clientSeason = -1;

	public StardewLeavesBlock(Properties properties) {
		this(properties, false);
	}

	public StardewLeavesBlock(Properties properties, boolean persistent) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(DORMANT, false).setValue(PERSISTENT, persistent));
	}

	public static void updateClientSeason(int season) {
		clientSeason = season;
	}

	/** Stored in the state so chunk render snapshots and lighting workers see the same value. */
	public static boolean dormant(BlockState state, BlockGetter getter) {
		// Pointed leaves used to be saved with dormant=true by older winter builds.
		// They now have a winter texture and must remain renderable even before the
		// chunk refresh has rewritten that legacy state.
		return state.hasProperty(DORMANT) && state.getValue(DORMANT) && losesLeavesInWinter(state);
	}

	public static boolean losesLeavesInWinter(BlockState state) {
		return state.is(ModBlocks.OAK_LEAVES.get()) || state.is(ModBlocks.OAK_LEAVES_QUESTION.get()) || state.is(ModBlocks.MAPLE_LEAVES.get())
				|| state.is(ModBlocks.MAHOGANY_LEAVES.get());
	}

	public static BlockState seasonalState(BlockState state, Level level) {
		if (!state.hasProperty(DORMANT)) return state;
		int season = level instanceof ServerLevel ? StardewTimeManager.get().getCurrentSeason() : clientSeason;
		boolean hidden = level.dimension().equals(ModDimensions.STARDEW_VALLEY)
				&& season == 3 && losesLeavesInWinter(state);
		return state.setValue(DORMANT, hidden);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(DORMANT);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return seasonalState(super.getStateForPlacement(context), context.getLevel());
	}

	@Override
	public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
		super.onPlace(state, level, pos, oldState, moved);
		if (!level.isClientSide) {
			BlockState updated = seasonalState(state, level);
			if (updated != state) level.setBlock(pos, updated, Block.UPDATE_CLIENTS);
		}
	}

	@Override
	public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
		return dormant(state, level) ? 0 : super.getLightBlock(state, level, pos);
	}

	@Override
	public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
		return dormant(state, level) ? state.getFluidState().isEmpty() : super.propagatesSkylightDown(state, level, pos);
	}

	@Override
	public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
		return dormant(state, level) ? 1.0F : super.getShadeBrightness(state, level, pos);
	}

	@Override
	public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
		return dormant(state, level) ? Shapes.empty() : super.getOcclusionShape(state, level, pos);
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return dormant(state, level) ? Shapes.empty() : super.getShape(state, level, pos, context);
	}

	@Override
	public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return dormant(state, level) ? Shapes.empty() : super.getCollisionShape(state, level, pos, context);
	}

	@SuppressWarnings("null")
	@Override
	public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
		BlockState updated = super.updateShape(state, direction, neighborState, level, pos, neighborPos);
		if (shouldFastDecay(updated)) {
			level.scheduleTick(pos, this, FAST_DECAY_DELAY);
		}
		return updated;
	}

	@SuppressWarnings("null")
	@Override
	public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		super.tick(state, level, pos, random);
		BlockState current = level.getBlockState(pos);
		if (current.is(this) && shouldFastDecay(current)) {
			level.destroyBlock(pos, true);
		}
	}

	@SuppressWarnings("null")
	@Override
	public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		super.randomTick(state, level, pos, random);
		BlockState current = level.getBlockState(pos);
		if (current.is(this) && shouldFastDecay(current)) {
			level.scheduleTick(pos, this, FAST_DECAY_DELAY + random.nextInt(4));
		}
	}

	private static boolean shouldFastDecay(BlockState state) {
		return state.hasProperty(PERSISTENT)
				&& state.hasProperty(DISTANCE)
				&& !state.getValue(PERSISTENT)
				&& state.getValue(DISTANCE) >= DECAY_DISTANCE;
	}
}
