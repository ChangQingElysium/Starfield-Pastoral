package com.stardew.craft.block.utility;

import com.stardew.craft.api.v1.internal.tree.StardewTreeRuntimeRegistry;
import com.stardew.craft.api.v1.tree.StardewTreeState;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.shape.ModelVoxelShapeCache;
import com.stardew.craft.blockentity.TapperBlockEntity;
import com.stardew.craft.blockentity.ModBlockEntities;
import com.stardew.craft.blockentity.UtilityDropHelper;
import com.stardew.craft.tree.WildTrees;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.common.ItemAbilities;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TapperBlock extends Block implements EntityBlock {
	/**
	 * Facing means the direction of the supporting tree block relative to the tapper.
	 * Example: FACING=NORTH -> the supported log/trunk is at pos.north().
	 */
	public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
	private static final VoxelShape[] SHAPES = ModelVoxelShapeCache.horizontalShapes("stardewcraft:block/utility/tapper", Direction.SOUTH);
	private static final int MAX_TAPPERS_PER_TREE = 4;

	@SuppressWarnings("null")
	public TapperBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(@SuppressWarnings("null") StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	@Nullable
	public BlockEntity newBlockEntity(@SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") BlockState state) {
		return new TapperBlockEntity(pos, state);
	}

	@Override
	@Nullable
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(@SuppressWarnings("null") Level level, @SuppressWarnings("null") BlockState state, @SuppressWarnings("null") BlockEntityType<T> type) {
		if (level.isClientSide) {
			return null;
		}
		if (type != ModBlockEntities.TAPPER.get()) {
			return null;
		}
		return (lvl, pos, st, be) -> TapperBlockEntity.serverTick(lvl, pos, st, (TapperBlockEntity) be);
	}

	@SuppressWarnings("null")
	@Override
	public BlockState getStateForPlacement(@SuppressWarnings("null") BlockPlaceContext context) {
		Direction face = context.getClickedFace();
		if (!face.getAxis().isHorizontal()) {
			return null;
		}

		Level level = context.getLevel();
		BlockPos placePos = context.getClickedPos();
		BlockPos trunkPos = placePos.relative(face.getOpposite());
		WildTrees.Def def = WildTrees.findTapperSupportDef(level, trunkPos);
		if (def != null) {
			BlockPos treeRoot = WildTrees.findTapperTreeRoot(level, trunkPos);
			if (treeRoot == null || hasReachedTapperLimit(level, treeRoot, def)) {
				return null;
			}
		} else {
			StardewTreeState addonTree =
					StardewTreeRuntimeRegistry.findAddonTapperSupport(level, trunkPos);
			if (addonTree == null || hasReachedAddonTapperLimit(level, addonTree)) {
				return null;
			}
		}

		// Place into the adjacent air block; FACING points back to the trunk.
		return defaultBlockState().setValue(FACING, face.getOpposite());
	}

	@SuppressWarnings("null")
	@Override
	public boolean canSurvive(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") net.minecraft.world.level.LevelReader level, @SuppressWarnings("null") BlockPos pos) {
		@SuppressWarnings("null")
		Direction supportDir = state.getValue(FACING);
		@SuppressWarnings("null")
		BlockPos supportPos = pos.relative(supportDir);
		return WildTrees.findTapperSupportDef(level, supportPos) != null
				|| StardewTreeRuntimeRegistry.findAddonTapperSupport(level, supportPos) != null;
	}

	@SuppressWarnings("null")
	@Override
	public BlockState updateShape(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") Direction direction, @SuppressWarnings("null") BlockState neighborState, @SuppressWarnings("null") net.minecraft.world.level.LevelAccessor level, @SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") BlockPos neighborPos) {
		if (direction == state.getValue(FACING) && !canSurvive(state, level, pos)) {
			// Don't silently disappear: schedule a tick to drop & remove.
			level.scheduleTick(pos, this, 1);
			return state;
		}
		return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
	}

	@SuppressWarnings("null")
	@Override
	protected List<ItemStack> getDrops(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") LootParams.Builder params) {
		// Always drop itself (block item), regardless of loot-table/data-pack issues.
		return List.of(new ItemStack(ModBlocks.TAPPER.get()));
	}

	@SuppressWarnings("null")
	@Override
	public void tick(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") ServerLevel level, @SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") RandomSource random) {
		if (!canSurvive(state, level, pos)) {
			level.destroyBlock(pos, true);
		}
	}

	@SuppressWarnings({ "null", "deprecation" })
	@Override
	public float getDestroyProgress(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") Player player, @SuppressWarnings("null") BlockGetter level, @SuppressWarnings("null") BlockPos pos) {
		// Ensure tool-tier differences apply even if a datapack accidentally overrides mineable/axe.
		if (player.getAbilities().instabuild) {
			return 1.0f;
		}
		@SuppressWarnings("null")
		float hardness = state.getDestroySpeed(level, pos);
		if (hardness == -1.0f) {
			return 0.0f;
		}

		ItemStack tool = player.getMainHandItem();
		float digSpeed = 1.0f;
		if (!tool.isEmpty() && tool.canPerformAction(ItemAbilities.AXE_DIG) && tool.getItem() instanceof TieredItem tiered) {
			digSpeed = tiered.getTier().getSpeed();
		}

		// Vanilla uses 30 when the player can harvest the block, otherwise 100.
		int divisor = player.hasCorrectToolForDrops(state) ? 30 : 100;
		return digSpeed / hardness / (float) divisor;
	}

	@SuppressWarnings("null")
	@Override
	public VoxelShape getShape(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") BlockGetter level, @SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") CollisionContext context) {
		return SHAPES[ModelVoxelShapeCache.horizontalIndex(state.getValue(FACING))];
	}

	@SuppressWarnings("null")
	@Override
	public InteractionResult useWithoutItem(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") Level level, @SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") Player player, @SuppressWarnings("null") BlockHitResult hitResult) {
		return interact(state, level, pos, player);
	}

	@SuppressWarnings("null")
	@Override
	protected ItemInteractionResult useItemOn(@SuppressWarnings("null") ItemStack stack, @SuppressWarnings("null") BlockState state,
			@SuppressWarnings("null") Level level, @SuppressWarnings("null") BlockPos pos,
			@SuppressWarnings("null") Player player, @SuppressWarnings("null") InteractionHand hand,
			@SuppressWarnings("null") BlockHitResult hitResult) {
		InteractionResult result = interact(state, level, pos, player);
		return result.consumesAction()
				? ItemInteractionResult.sidedSuccess(level.isClientSide)
				: ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
	}

	private InteractionResult interact(BlockState state, Level level, BlockPos pos, Player player) {
		if (level.isClientSide) {
			return InteractionResult.SUCCESS;
		}

		@SuppressWarnings("null")
		BlockEntity be = level.getBlockEntity(pos);
		if (!(be instanceof TapperBlockEntity tapper)) {
			return InteractionResult.PASS;
		}
		startCycleIfNeeded(level, pos, state, tapper);

		UtilityDropHelper.tryHarvest(level, pos, player, tapper::isReady, tapper::harvestOne,
				UtilityDropHelper.LOW_MACHINE_VANILLA_XP);
		return InteractionResult.CONSUME;
	}

	@SuppressWarnings("null")
	@Override
	public void onRemove(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") Level level, @SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") BlockState newState, boolean isMoving) {
		if (!state.is(newState.getBlock()) && !isMoving) {
			UtilityDropHelper.dropAutomationContents(level, pos);
		}
		super.onRemove(state, level, pos, newState, isMoving);
	}

	@SuppressWarnings("null")
	@Override
	public void onPlace(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") Level level, @SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") BlockState oldState, boolean isMoving) {
		super.onPlace(state, level, pos, oldState, isMoving);
		if (!level.isClientSide) {
			@SuppressWarnings("null")
			BlockEntity be = level.getBlockEntity(pos);
			if (be instanceof TapperBlockEntity tapper) {
				startCycleIfNeeded(level, pos, state, tapper);
			}
		}
	}

	private static void startCycleIfNeeded(Level level, BlockPos pos, BlockState state, TapperBlockEntity tapper) {
		// Start cycle lazily on first interaction or on placement.
		if (tapper.hasProduct()) {
			return;
		}
		WildTrees.Def def = findValidProductionDef(level, pos, state);
		if (def != null) {
			tapper.startCycleIfEmpty(def.id());
			return;
		}
		StardewTreeState addonTree = findValidAddonProductionState(level, pos, state);
		if (addonTree != null) {
			tapper.startAddonCycleIfEmpty(addonTree);
		}
	}

	@Nullable
	public static WildTrees.Def findValidProductionDef(LevelReader level, BlockPos pos, BlockState state) {
		if (!state.is(ModBlocks.TAPPER.get()) || !state.hasProperty(FACING)) {
			return null;
		}
		@SuppressWarnings("null")
		Direction supportDir = state.getValue(FACING);
		@SuppressWarnings("null")
		BlockPos supportPos = pos.relative(supportDir);
		WildTrees.Def def = WildTrees.findTapperSupportDef(level, supportPos);
		if (def == null) {
			return null;
		}
		BlockPos treeRoot = WildTrees.findTapperTreeRoot(level, supportPos);
		if (treeRoot == null) {
			return null;
		}
		int tappers = countTappersOnTree(level, treeRoot, def);
		return tappers > 0 && tappers <= MAX_TAPPERS_PER_TREE ? def : null;
	}

	public static boolean isValidProductionSite(LevelReader level, BlockPos pos, BlockState state) {
		return findValidProductionDef(level, pos, state) != null
				|| findValidAddonProductionState(level, pos, state) != null;
	}

	@Nullable
	public static StardewTreeState findValidAddonProductionState(
			LevelReader level,
			BlockPos pos,
			BlockState state
	) {
		if (!state.is(ModBlocks.TAPPER.get()) || !state.hasProperty(FACING)) {
			return null;
		}
		Direction supportDir = state.getValue(FACING);
		BlockPos supportPos = pos.relative(supportDir);
		StardewTreeState tree =
				StardewTreeRuntimeRegistry.findAddonTapperSupport(level, supportPos);
		if (tree == null) {
			return null;
		}
		int tappers = countAddonTappersOnTree(level, tree);
		return tappers > 0 && tappers <= MAX_TAPPERS_PER_TREE ? tree : null;
	}

	private static boolean hasReachedTapperLimit(LevelReader level, BlockPos treeRoot, WildTrees.Def def) {
		return countTappersOnTree(level, treeRoot, def) >= MAX_TAPPERS_PER_TREE;
	}

	private static boolean hasReachedAddonTapperLimit(
			LevelReader level,
			StardewTreeState tree
	) {
		return countAddonTappersOnTree(level, tree) >= MAX_TAPPERS_PER_TREE;
	}

	private static int countAddonTappersOnTree(LevelReader level, StardewTreeState tree) {
		Set<BlockPos> tappers = new HashSet<>();
		for (BlockPos support : tree.tapperSupports()) {
			collectAdjacentTappers(level, support, tappers);
		}
		return tappers.size();
	}

	private static int countTappersOnTree(LevelReader level, BlockPos treeRoot, WildTrees.Def def) {
		Set<BlockPos> tappers = new HashSet<>();
		if (def.isModernRoot(level.getBlockState(treeRoot))) {
			WildTrees.forEachLiveGeneratedModernLogInTree(level, treeRoot, def, logPos -> {
				collectAdjacentTappers(level, logPos, tappers);
			});
			return tappers.size();
		}
		collectAdjacentTappers(level, treeRoot, tappers);
		return tappers.size();
	}

	private static boolean hasAdjacentTapper(LevelReader level, BlockPos pos) {
		Set<BlockPos> tappers = new HashSet<>();
		collectAdjacentTappers(level, pos, tappers);
		return !tappers.isEmpty();
	}

	private static void collectAdjacentTappers(LevelReader level, BlockPos pos, Set<BlockPos> tappers) {
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockPos tapperPos = pos.relative(d);
			if (level.getBlockState(tapperPos).is(ModBlocks.TAPPER.get())) {
				tappers.add(tapperPos.immutable());
			}
		}
	}

	@SuppressWarnings("null")
	@Override
	public BlockState rotate(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") Rotation rot) {
		return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
	}

	@SuppressWarnings("null")
	@Override
	public BlockState mirror(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") Mirror mirror) {
		return rotate(state, mirror.getRotation(state.getValue(FACING)));
	}

	@SuppressWarnings("null")
	@Override
	public net.minecraft.world.level.material.FluidState getFluidState(@SuppressWarnings("null") BlockState state) {
		return super.getFluidState(state);
	}
}
