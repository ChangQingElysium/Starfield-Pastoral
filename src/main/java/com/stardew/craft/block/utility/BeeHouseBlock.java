package com.stardew.craft.block.utility;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.shape.ModelVoxelShapeCache;
import com.stardew.craft.blockentity.BeeHouseBlockEntity;
import com.stardew.craft.blockentity.ModBlockEntities;
import com.stardew.craft.blockentity.UtilityDropHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import com.stardew.craft.port.net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.util.StringRepresentable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * Bee House - produces honey.
 */
public class BeeHouseBlock extends Block implements EntityBlock {
	public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final BooleanProperty READY = BooleanProperty.create("ready");
	public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);

	private static final VoxelShape[] MAIN_SHAPES = ModelVoxelShapeCache.horizontalShapes("stardewcraft:block/utility/bee_house", Direction.NORTH);
	private static final VoxelShape[] EXT_SHAPES = ModelVoxelShapeCache.horizontalShapes("stardewcraft:block/utility/bee_house_extension", Direction.NORTH);

	public enum Part implements StringRepresentable {
		MAIN("main"),
		EXTENSION("extension");

		private final String name;

		Part(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return name;
		}
	}

	@SuppressWarnings("null")
	public BeeHouseBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any()
			.setValue(FACING, Direction.NORTH)
			.setValue(READY, false)
			.setValue(PART, Part.MAIN));
	}

	@Override
	protected void createBlockStateDefinition(@Nonnull StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, READY, PART);
	}

	@SuppressWarnings("null")
	@Override
	protected List<ItemStack> getDrops(@Nonnull BlockState state, @Nonnull LootParams.Builder params) {
		if (state.getValue(PART) == Part.EXTENSION) {
			return List.of();
		}
		return List.of(new ItemStack(ModBlocks.BEE_HOUSE.get()));
	}

	@Override
	@SuppressWarnings("null")
	public boolean canSurvive(@Nonnull BlockState state, @Nonnull LevelReader level, @Nonnull BlockPos pos) {
		Part part = state.getValue(PART);
		if (part == Part.EXTENSION) {
			BlockState belowState = level.getBlockState(pos.below());
			return belowState.is(this) && belowState.getValue(PART) == Part.MAIN;
		}
		BlockState aboveState = level.getBlockState(pos.above());
		return aboveState.isAir() || (aboveState.is(this) && aboveState.getValue(PART) == Part.EXTENSION);
	}

	@Nullable
	@Override
	@SuppressWarnings("null")
	public BlockState getStateForPlacement(@Nonnull BlockPlaceContext context) {
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		BlockPos extensionPos = pos.above();
		if (!level.getWorldBorder().isWithinBounds(extensionPos)) {
			return null;
		}
		if (!level.getBlockState(extensionPos).canBeReplaced(context)) {
			return null;
		}
		BlockState state = defaultBlockState()
			.setValue(FACING, context.getHorizontalDirection().getOpposite())
			.setValue(READY, false)
			.setValue(PART, Part.MAIN);
		return canSurvive(state, level, pos) ? state : null;
	}

	@SuppressWarnings("null")
	@Override
	public void setPlacedBy(@SuppressWarnings("null") Level level, @SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") BlockState state, @Nullable net.minecraft.world.entity.LivingEntity placer, @SuppressWarnings("null") ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (level.isClientSide) {
			return;
		}
		if (state.getValue(PART) != Part.MAIN) {
			return;
		}
		BlockPos extensionPos = pos.above();
		BlockState extensionState = state.setValue(PART, Part.EXTENSION);
		level.setBlock(extensionPos, extensionState, 3);
	}

	@SuppressWarnings("null")
	@Override
	public BlockState rotate(@Nonnull BlockState state, @Nonnull net.minecraft.world.level.block.Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@SuppressWarnings("null")
	@Override
	public BlockState mirror(@Nonnull BlockState state, @Nonnull net.minecraft.world.level.block.Mirror mirror) {
		return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
	}

	@SuppressWarnings("null")
	@Override
	public BlockState updateShape(@Nonnull BlockState state, @Nonnull Direction direction, @Nonnull BlockState neighborState, @Nonnull LevelAccessor level, @Nonnull BlockPos pos, @Nonnull BlockPos neighborPos) {
		Part part = state.getValue(PART);
		BlockPos otherPos = part == Part.MAIN ? pos.above() : pos.below();
		if (neighborPos.equals(otherPos) && !neighborState.is(this)) {
			return Blocks.AIR.defaultBlockState();
		}
		if ((direction == Direction.UP || direction == Direction.DOWN) && !canSurvive(state, level, pos)) {
			level.scheduleTick(pos, this, 1);
			return state;
		}
		return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
	}

	@Override
	public void tick(@Nonnull BlockState state, @Nonnull ServerLevel level, @Nonnull BlockPos pos, @Nonnull RandomSource random) {
		if (!canSurvive(state, level, pos)) {
			level.destroyBlock(pos, true);
		}
	}

	@SuppressWarnings("null")
	@Override
	public VoxelShape getShape(@Nonnull BlockState state, @Nonnull BlockGetter level, @Nonnull BlockPos pos, @Nonnull CollisionContext context) {
		return getPartShape(state);
	}

	@Override
	public VoxelShape getCollisionShape(@Nonnull BlockState state, @Nonnull BlockGetter level, @Nonnull BlockPos pos, @Nonnull CollisionContext context) {
		return getPartShape(state);
	}

	@SuppressWarnings("null")
	private static VoxelShape getPartShape(BlockState state) {
		int index = ModelVoxelShapeCache.horizontalIndex(state.getValue(FACING));
		return state.getValue(PART) == Part.EXTENSION ? EXT_SHAPES[index] : MAIN_SHAPES[index];
	}

	@SuppressWarnings("null")
	@Override
	@Nullable
	public BlockEntity newBlockEntity(@Nonnull BlockPos pos, @Nonnull BlockState state) {
		if (state.getValue(PART) == Part.EXTENSION) {
			return null;
		}
		return new BeeHouseBlockEntity(pos, state);
	}

	@SuppressWarnings("null")
	@Override
	@Nullable
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(@Nonnull Level level, @Nonnull BlockState state, @Nonnull BlockEntityType<T> type) {
		if (state.getValue(PART) == Part.EXTENSION) {
			return null;
		}
		if (type != ModBlockEntities.BEE_HOUSE.get()) {
			return null;
		}
		if (level.isClientSide) {
			return null;
		}
		return (lvl, pos, st, be) -> BeeHouseBlockEntity.serverTick(lvl, pos, st, (BeeHouseBlockEntity) be);
	}

	@SuppressWarnings("null")
	@Override
	protected ItemInteractionResult useItemOn(@Nonnull ItemStack stack, @Nonnull BlockState state, @Nonnull Level level, @Nonnull BlockPos pos, @Nonnull Player player, @Nonnull InteractionHand hand, @Nonnull BlockHitResult hit) {
		if (state.getValue(PART) == Part.EXTENSION) {
			BlockPos mainPos = getMainPos(pos, state);
			BlockState mainState = level.getBlockState(mainPos);
			return useItemOn(stack, mainState, level, mainPos, player, hand, hit);
		}

		if (level.isClientSide) {
			return ItemInteractionResult.sidedSuccess(true);
		}

		BlockEntity be = level.getBlockEntity(pos);
		if (!(be instanceof BeeHouseBlockEntity beeHouse)) {
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		}

		return UtilityDropHelper.tryHarvest(level, pos, player, beeHouse::isReady, () -> beeHouse.harvestOne(player),
				UtilityDropHelper.STANDARD_MACHINE_VANILLA_XP)
			? ItemInteractionResult.sidedSuccess(false)
			: ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
	}

	@SuppressWarnings("null")
	@Override
	protected InteractionResult useWithoutItem(@Nonnull BlockState state, @Nonnull Level level, @Nonnull BlockPos pos, @Nonnull Player player, @Nonnull BlockHitResult hit) {
		if (state.getValue(PART) == Part.EXTENSION) {
			BlockPos mainPos = getMainPos(pos, state);
			BlockState mainState = level.getBlockState(mainPos);
			return useWithoutItem(mainState, level, mainPos, player, hit);
		}

		if (level.isClientSide) {
			return InteractionResult.SUCCESS;
		}

		BlockEntity be = level.getBlockEntity(pos);
		if (!(be instanceof BeeHouseBlockEntity beeHouse)) {
			return InteractionResult.PASS;
		}

		return UtilityDropHelper.tryHarvest(level, pos, player, beeHouse::isReady, () -> beeHouse.harvestOne(player),
				UtilityDropHelper.STANDARD_MACHINE_VANILLA_XP)
			? InteractionResult.CONSUME
			: InteractionResult.PASS;
	}

	@SuppressWarnings("null")
	@Override
	public void onRemove(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") Level level, @SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") BlockState newState, boolean isMoving) {
		if (!state.is(newState.getBlock())) {
			if (!isMoving && state.getValue(PART) == Part.MAIN) {
				UtilityDropHelper.dropAutomationContents(level, pos);
			}
			Part part = state.getValue(PART);
			BlockPos otherPos = part == Part.MAIN ? pos.above() : pos.below();
			BlockState otherState = level.getBlockState(otherPos);
			if (otherState.getBlock() == this && otherState.getValue(PART) != part) {
				level.removeBlock(otherPos, false);
			}
		}
		super.onRemove(state, level, pos, newState, isMoving);
	}

	@SuppressWarnings("null")
	@Override
	public BlockState playerWillDestroy(@SuppressWarnings("null") Level level, @SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") BlockState state, @SuppressWarnings("null") Player player) {
		if (!level.isClientSide && state.getValue(PART) == Part.EXTENSION && !player.isCreative()) {
			popResource(level, pos, new ItemStack(ModBlocks.BEE_HOUSE.get()));
		}
		return super.playerWillDestroy(level, pos, state, player);
	}

	@SuppressWarnings("null")
	public static BlockPos getMainPos(BlockPos pos, BlockState state) {
		return state.getValue(PART) == Part.EXTENSION ? pos.below() : pos;
	}

	@SuppressWarnings("null")
	public static BlockPos getExtensionPos(BlockPos pos, BlockState state) {
		return state.getValue(PART) == Part.EXTENSION ? pos : pos.above();
	}
}
